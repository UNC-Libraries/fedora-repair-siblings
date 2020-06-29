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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.fcrepo.client.FcrepoOperationFailedException;
import org.fcrepo.client.FcrepoResponse;
import org.slf4j.Logger;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Parameters;

/**
 * @author bbpennel
 */
@Command(name = "repair")
public class RepairIssuesCommand implements Callable<Integer> {
    private static final Logger output = getLogger("output");

    @Mixin
    private CommonOptions common;

    @Parameters(index = "0", description = "File listing paths to repair")
    private Path pathList;

    @SuppressWarnings("deprecation")
    @Override
    public Integer call() throws Exception {
        Pattern pattern = Pattern.compile("(?<prob>(?<pre>.*)" + common.fixPattern + ")(?<post>.*)");

        // Compute the mapping of pairtree nodes with sibling indexes to contained children
        Map<String, Set<String>> problemToContained = new HashMap<>();
        try (Stream<String> stream = Files.lines(pathList)) {
            stream.forEach(line -> {
                Matcher matcher = pattern.matcher(line);
                if (!matcher.matches()) {
                    output.error("Unexpected path in provided list {}", line);
                    return;
                }
                String prob = matcher.group("prob");
                if (prob.equals(line)) {
                    output.debug("Ignoring {}", prob);
                    return;
                }
                if (!exists(URI.create(prob))) {
                    output.warn("Ignoring listed resource which does not exist: {}", prob);
                    return;
                }

                Set<String> contained;
                if (problemToContained.containsKey(prob)) {
                    contained = problemToContained.get(prob);
                } else {
                    contained = new HashSet<>();
                    problemToContained.put(prob, contained);
                }
                contained.add(line);
            });
        } catch (IOException e) {
            output.error("Failed to read input file", e);
            return 1;
        }

        // Rename the pairtrees with the [] in reverse order to deal with indexes adjusting as sibling count changes
        List<URI> destUris = new ArrayList<>();
        problemToContained.keySet().stream()
                .sorted(Comparator.reverseOrder())
                .forEach(prob -> {
            Matcher matcher = pattern.matcher(prob);
            if (!matcher.matches()) {
                output.error("Skipping unexpected path {}", prob);
                return;
            }

            String pre = matcher.group("pre");
            String fix = matcher.group("fix");

            String newBase = pre + "_" + fix;
            URI sourceUri = URI.create(prob);
            URI destUri = URI.create(newBase);

            removeProblemCharacters(sourceUri, destUri, pattern, true);

            destUris.add(destUri);
        });

        // Move children of the renamed pair trees to the pairtree path without sibling indexes
        problemToContained.forEach((prob, containedList) -> {
            // Check if the destination exists
            for (String contained : containedList) {
                Matcher cMatcher = pattern.matcher(contained);
                cMatcher.matches();

                String preC = cMatcher.group("pre");
                String fixC = cMatcher.group("fix");
                String postC = cMatcher.group("post");

                URI fixedUri = URI.create(preC + postC);
                if (exists(fixedUri)) {
                    output.error("Skipping move of {}, resource exists at destination {}",
                            contained, fixedUri);
                    return;
                }

                String movedPath = preC + "_" + fixC + postC;
                String fixedParent = preC + postC;
                fixedParent = StringUtils.substringBeforeLast(fixedParent, "/");
                URI fixedParentUri = URI.create(fixedParent);

                if (!exists(fixedParentUri)) {
                    output.debug("Creating parent {}", fixedParentUri);
                    if (!common.dryRun) {
                        createParent(fixedParentUri);
                    }
                }

                URI movedUri = URI.create(movedPath);

                output.info("Moving contained {} to {}", movedUri, fixedUri);
                if (!common.dryRun) {
                    try (FcrepoResponse resp = common.getClient()
                            .move(movedUri, fixedUri)
                            .perform()) {
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to move " + movedUri, e);
                    } catch (FcrepoOperationFailedException e) {
                        if (e.getStatusCode() == HttpStatus.SC_CONFLICT) {
                            if (exists(movedUri)) {
                                // expected
                                return;
                            }
                        }
                        throw new RuntimeException("Failed to move " + movedUri, e);
                    }
                }
            }
        });

        destUris.forEach(destUri -> cleanupEmpty(destUri));

        return 0;
    }

    @SuppressWarnings("deprecation")
    private void removeProblemCharacters(URI sourceUri, URI destUri, Pattern pattern, boolean retry) {
        output.info("Renaming parent {} to {}", sourceUri, destUri);

        if (common.dryRun) {
            return;
        }

        try (FcrepoResponse resp = common.getClient()
                .move(sourceUri, destUri)
                .perform()) {

        } catch (IOException e) {
            throw new RuntimeException("Failed to move " + sourceUri, e);
        } catch (FcrepoOperationFailedException e) {
            if (e.getStatusCode() == HttpStatus.SC_PRECONDITION_FAILED) {
                boolean cleanedUpSource = cleanupEmpty(sourceUri);
                cleanupEmpty(destUri);
                if (retry && !cleanedUpSource) {
                    removeProblemCharacters(sourceUri, destUri, pattern, false);
                }
                return;
            }
            if (e.getStatusCode() == HttpStatus.SC_CONFLICT) {
                if (exists(destUri)) {
                    // expected
                    return;
                }
            }
            throw new RuntimeException("Failed to move " + sourceUri, e);
        }
    }

    private boolean exists(URI uri) {
        try (FcrepoResponse resp = common.getClient().head(uri).perform()) {
            return true;
        } catch (IOException e) {
            throw new RuntimeException("Failed to check on " + uri, e);
        } catch (FcrepoOperationFailedException e) {
            if (e.getStatusCode() == HttpStatus.SC_NOT_FOUND) {
                return false;
            } else {
                throw new RuntimeException("Failed to check on " + uri, e);
            }
        }
    }

    private void createParent(URI uri) {
        try (FcrepoResponse resp = common.getClient().put(uri).perform()) {

        } catch (IOException | FcrepoOperationFailedException e) {
            throw new RuntimeException("Failed to create parent " + uri, e);
        }
    }

    private boolean cleanupEmpty(URI uri) {
        try (FcrepoResponse resp = common.getClient().get(uri).perform()) {
            Model model = RepairCLI.createModel(resp.getBody());
            if (model.contains(null, RepairCLI.ldp_contains, (RDFNode) null)) {
                output.error("Cannot cleanup non-empty object {}", uri);
                return false;
            }
            if (common.dryRun) {
                return true;
            }
            try (FcrepoResponse delResp = common.getClient().delete(uri).perform()) {
                URI tombUri = URI.create(uri.toString() + "/fcr:tombstone");
                try (FcrepoResponse tombResp = common.getClient().delete(tombUri).perform()) {
                }
            }
            return true;
        } catch (IOException e) {
            output.error("Failed to cleanup {}", uri, e);
        } catch (FcrepoOperationFailedException e) {
            if (e.getStatusCode() == HttpStatus.SC_NOT_FOUND) {
                output.debug("Skipping cleanup, resource does not exist {}", uri, e.getMessage());
                return false;
            }
            output.error("Failed to cleanup {}", uri, e.getMessage());
        }
        return false;
    }
}
