/*
 * Copyright 2018 ThoughtWorks, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.thoughtworks.go.server.materials;

import com.thoughtworks.go.config.materials.svn.SvnMaterial;
import com.thoughtworks.go.helper.MaterialsMother;
import com.thoughtworks.go.server.cronjob.GoDiskSpaceMonitor;
import com.thoughtworks.go.server.perf.MDUPerformanceLogger;
import com.thoughtworks.go.server.service.DrainModeService;
import com.thoughtworks.go.server.transaction.TransactionCallback;
import com.thoughtworks.go.server.transaction.TransactionTemplate;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import static org.mockito.Mockito.*;

public class MaterialUpdateListenerTest {
    private MaterialUpdateListener materialUpdateListener;
    private MaterialUpdateCompletedTopic topic;
    private MaterialDatabaseUpdater updater;
    private static final SvnMaterial MATERIAL = MaterialsMother.svnMaterial();
    private GoDiskSpaceMonitor diskSpaceMonitor;
    private TransactionTemplate transactionTemplate;
    private MDUPerformanceLogger mduPerformanceLogger;
    private DrainModeService drainModeService;

    @Before
    public void setUp() throws Exception {
        topic = mock(MaterialUpdateCompletedTopic.class);
        updater = mock(MaterialDatabaseUpdater.class);
        diskSpaceMonitor = mock(GoDiskSpaceMonitor.class);
        transactionTemplate = mock(TransactionTemplate.class);
        mduPerformanceLogger = mock(MDUPerformanceLogger.class);
        drainModeService = mock(DrainModeService.class);
        materialUpdateListener = new MaterialUpdateListener(topic, updater, mduPerformanceLogger, diskSpaceMonitor, drainModeService);
    }

    @Test
    public void shouldNotUpdateOnMessageWhenLowOnDisk() throws Exception {
        when(diskSpaceMonitor.isLowOnDisk()).thenReturn(true);
        materialUpdateListener.onMessage(new MaterialUpdateMessage(MATERIAL, 0));
        verify(updater, never()).updateMaterial(MATERIAL);
    }

    @Test
    public void shouldNotUpdateOnMessageWhenServerIsInDrainMode() {
        when(drainModeService.isDrainMode()).thenReturn(true);
        materialUpdateListener.onMessage(new MaterialUpdateMessage(MATERIAL, 0));
        verifyZeroInteractions(updater);
    }

    @Test
    public void shouldUpdateMaterialOnMessage() throws Exception {
        setupTransactionTemplateStub();
        materialUpdateListener.onMessage(new MaterialUpdateMessage(MATERIAL, 0));
        verify(updater).updateMaterial(MATERIAL);
    }

    @Test
    public void shouldNotifyDrainModeServiceAboutStartOfMaterialUpdate() throws Exception {
        setupTransactionTemplateStub();
        materialUpdateListener.onMessage(new MaterialUpdateMessage(MATERIAL, 0));
        verify(updater).updateMaterial(MATERIAL);
        verify(drainModeService).mduStartedForMaterial(MATERIAL);
        verify(drainModeService).mduFinishedForMaterial(MATERIAL);
    }

    private void setupTransactionTemplateStub() throws Exception {
        when(transactionTemplate.executeWithExceptionHandling(Mockito.any(TransactionCallback.class))).thenAnswer(new Answer<Object>() {
            public Object answer(InvocationOnMock invocationOnMock) throws Throwable {
                TransactionCallback callback = (TransactionCallback) invocationOnMock.getArguments()[0];
                callback.doInTransaction(null);
                return null;
            }
        });
    }

    @Test
    public void shouldPostOnCompleteMessage() {
        materialUpdateListener.onMessage(new MaterialUpdateMessage(MATERIAL, 20));
        verify(topic).post(new MaterialUpdateSuccessfulMessage(MATERIAL, 20));
    }

    @Test
    public void shouldPostUpdateFailedMessageOnException() throws Exception {
        setupTransactionTemplateStub();
        Exception exception = new Exception();
        doThrow(exception).when(updater).updateMaterial(MATERIAL);
        materialUpdateListener.onMessage(new MaterialUpdateMessage(MATERIAL, 10));
        verify(topic).post(new MaterialUpdateFailedMessage(MATERIAL, 10, exception));
    }

    @Test
    public void shouldPostUpdateSkippedMessageWhenServerIsInDrainMode() {
        when(drainModeService.isDrainMode()).thenReturn(true);
        materialUpdateListener.onMessage(new MaterialUpdateMessage(MATERIAL, 10));
        verify(topic).post(new MaterialUpdateSkippedMessage(MATERIAL, 10));
    }
}
