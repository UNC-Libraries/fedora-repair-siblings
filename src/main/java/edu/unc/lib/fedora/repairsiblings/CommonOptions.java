/**
 * Copyright 2008 The University of North Carolina at Chapel Hill
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package edu.unc.lib.fedora.repairsiblings;

import java.net.URI;

import org.fcrepo.client.FcrepoClient;

import picocli.CommandLine.Option;

/**
 * @author bbpennel
 */
public class CommonOptions {

    private FcrepoClient fcrepoClient;

    @Option(names = {"-u", "--username"},
            description = "Fedora user for basic authentication")
    protected String username;

    @Option(names = {"-p", "--password"},
            description = "Passphrase",
            interactive = true)
    protected String password;

    @Option(names = {"-b", "--fedora-url"},
            defaultValue = "http://localhost:8080/fcrepo/rest",
            description = "Fedora base url. Default is http://localhost:8080/fcrepo/rest")
    protected String fedoraBase;

    @Option(names = {"-f", "--fix-pattern"},
            defaultValue = "%5B(?<fix>\\d+)%5D",
            description = "Pattern to fix, defaults to [(?<fix>\\d+)]")
    protected String fixPattern;

    @Option(names = {"-n", "--dry-run"},
            defaultValue = "false")
    protected boolean dryRun;

    protected FcrepoClient getClient() {
        if (fcrepoClient == null) {
            if (username == null) {
                fcrepoClient = FcrepoClient.client()
                        .throwExceptionOnFailure()
                        .build();
            } else {
                URI fedoraUri = URI.create(fedoraBase);
                fcrepoClient = FcrepoClient.client()
                        .authScope(fedoraUri.getHost())
                        .credentials(username, password)
                        .throwExceptionOnFailure()
                        .build();
            }
        }
        return fcrepoClient;
    }
}
