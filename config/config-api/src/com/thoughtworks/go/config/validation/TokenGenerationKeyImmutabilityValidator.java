/*
 * Copyright 2017 ThoughtWorks, Inc.
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

package com.thoughtworks.go.config.validation;

import com.thoughtworks.go.config.CruiseConfig;
import com.thoughtworks.go.config.ServerConfig;
import com.thoughtworks.go.util.SystemEnvironment;

/**
 * @understands: ensures tokenGenerationKey is never changed
 */
public class TokenGenerationKeyImmutabilityValidator implements GoConfigValidator {
    private final SystemEnvironment systemEnvironment;
    private String tokenGenerationKey;

    public TokenGenerationKeyImmutabilityValidator(SystemEnvironment systemEnvironment) {
        this.systemEnvironment = systemEnvironment;
    }

    public void validate(CruiseConfig cruiseConfig) throws Exception {
        ServerConfig server = cruiseConfig.server();
        String newTokenGenerationKey = server.getTokenGenerationKey();
        if (tokenGenerationKey == null) {
            tokenGenerationKey = newTokenGenerationKey;
        }

        if (tokenGenerationKey == null || tokenGenerationKey.equals(newTokenGenerationKey) || ! systemEnvironment.enforceServerImmutability() ) {
            return;
        }
        throw new RuntimeException("The value of 'tokenGenerationKey' cannot be modified while the server is online. If you really want to make this change, you may do so while the server is offline. Please note: updating 'tokenGenerationKey' will invalidate all registration tokens issued to the agents so far.");
    }

    protected String getTokenGenerationKey() {
        return tokenGenerationKey;
    }
}