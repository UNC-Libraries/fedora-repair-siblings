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

import static org.slf4j.LoggerFactory.getLogger;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.Callable;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.NodeIterator;
import org.fcrepo.client.FcrepoOperationFailedException;
import org.fcrepo.client.FcrepoResponse;
import org.slf4j.Logger;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

/**
 * @author bbpennel
 */
@Command(name = "locate")
public class LocateIssuesCommand implements Callable<Integer> {
    private static final Logger log = getLogger(LocateIssuesCommand.class);
    private static final Logger output = getLogger("output");

    @Mixin
    private CommonOptions common;

    @Option(names = {"-r", "--recursive"},
            description = "Recurse through the containment hierarchy")
    protected boolean recursive;

    @Override
    public Integer call() throws Exception {
        crawlContains(URI.create(common.fedoraBase));

        return 0;
    }

    private void crawlContains(URI rescUri) {
        log.info("Retrieving {}", rescUri);
        try (FcrepoResponse resp = common.getClient().head(rescUri).perform()) {
            if (resp.hasType(RepairCLI.BINARY_TYPE_URI)) {
                return;
            }
        } catch (IOException | FcrepoOperationFailedException e) {
            log.error("Failed to retrieve {}", rescUri, e);
        }

        try (FcrepoResponse resp = common.getClient().get(rescUri).perform()) {
            Model model = RepairCLI.createModel(resp.getBody());
            NodeIterator objIt = model.listObjectsOfProperty(RepairCLI.ldp_contains);
            while (objIt.hasNext()) {
                String containedString = objIt.next().toString();
                if (containedString.matches(".*" + common.fixPattern + ".*")) {
                    output.info(containedString);
                }
                if (recursive) {
                    crawlContains(URI.create(containedString));
                }
            }
        } catch (IOException | FcrepoOperationFailedException e) {
            log.error("Failed to retrieve {}", rescUri, e);
        }
    }
}
