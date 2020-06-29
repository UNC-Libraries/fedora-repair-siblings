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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.apache.http.HttpStatus;
import org.fcrepo.client.FcrepoClient;
import org.fcrepo.client.FcrepoOperationFailedException;
import org.fcrepo.client.FcrepoResponse;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.ContextHierarchy;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import picocli.CommandLine;

/**
 * @author bbpennel
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextHierarchy({
    @ContextConfiguration("/spring-test/test-fedora-container.xml"),
})
public class RepairIT {

    private String serverAddress = "http://localhost:48085";

    private FcrepoClient fcrepoClient;

    final PrintStream originalOut = System.out;
    final ByteArrayOutputStream out = new ByteArrayOutputStream();

    @Before
    public void setUp() throws Exception {
        fcrepoClient = FcrepoClient.client().build();

        System.setOut(new PrintStream(out));
        out.reset();
    }

    @Test
    public void locateAndRepair() throws Exception {
        final String baseId = UUID.randomUUID().toString();
        URI baseUri = URI.create(serverAddress + "/" + baseId);

        try (FcrepoResponse resp = fcrepoClient.put(baseUri).perform()) {
            assertEquals(HttpStatus.SC_CREATED, resp.getStatusCode());
        }

        generateSiblingNodes(baseId);

        CommandLine command = new CommandLine(new RepairCLI());

        // Locate all the problems generated
        String[] args = new String[] { "locate",
                "-b", baseUri.toString() };
        int result = command.execute(args);
        assertEquals("Incorrect exit status", 0, result);
        List<String> output = Arrays.asList(out.toString().trim().split("\n"));
        assertTrue(output.size() > 0);

        out.reset();

        Path locateResults = Files.createTempFile("locate", ".txt");
        Files.write(locateResults, output, UTF_8);

        System.setOut(originalOut);

        // Repair the problems
        String[] args2 = new String[] { "repair", locateResults.toString(),
                "-b", baseUri.toString() };
        int result2 = command.execute(args2);
        assertEquals("Incorrect exit status", 0, result2);

        System.setOut(new PrintStream(out));
        out.reset();

        String[] args3 = new String[] { "locate",
                "-b", baseUri.toString() };
        int result3 = command.execute(args3);
        assertEquals("Incorrect exit status", 0, result3);
        System.setOut(originalOut);

        assertTrue(out.toString().trim().isEmpty());
    }

    @Test
    public void dryRun() throws Exception {
        final String baseId = UUID.randomUUID().toString();
        URI baseUri = URI.create(serverAddress + "/" + baseId);

        try (FcrepoResponse resp = fcrepoClient.put(baseUri).perform()) {
            assertEquals(HttpStatus.SC_CREATED, resp.getStatusCode());
        }

        generateSiblingNodes(baseId);

        CommandLine command = new CommandLine(new RepairCLI());

        // Locate all the problems generated
        String[] args = new String[] { "locate",
                "-b", baseUri.toString() };
        int result = command.execute(args);
        assertEquals("Incorrect exit status", 0, result);
        List<String> output = Arrays.asList(out.toString().trim().split("\n"));
        assertTrue(output.size() > 0);

        out.reset();

        Path locateResults = Files.createTempFile("locate", ".txt");
        Files.write(locateResults, output, UTF_8);

        System.setOut(originalOut);

        // Repair the problems
        String[] args2 = new String[] { "repair", locateResults.toString(),
                "-b", baseUri.toString(),
                "-n" };
        int result2 = command.execute(args2);
        assertEquals("Incorrect exit status", 0, result2);

        System.setOut(new PrintStream(out));
        out.reset();

        String[] args3 = new String[] { "locate",
                "-b", baseUri.toString() };
        int result3 = command.execute(args3);
        assertEquals("Incorrect exit status", 0, result3);
        System.setOut(originalOut);

        List<String> output2 = Arrays.asList(out.toString().trim().split("\n"));

        assertEquals(output.size(), output2.size());
        assertTrue(output.containsAll(output2));
    }

    private void generateSiblingNodes(String baseId) throws Exception {
        List<String> txIds = new ArrayList<>();
        List<Thread> threads = new ArrayList<>();

        for (int i = 0; i < 10; i++) {
            final String txLocation;
            URI startTxUri = URI.create(serverAddress + "/fcr:tx");

            try (FcrepoResponse resp = fcrepoClient.post(startTxUri).perform()) {
                assertEquals(HttpStatus.SC_CREATED, resp.getStatusCode());
                txLocation = resp.getLocation().toString();
                txIds.add(txLocation);
            }

            final String objId = baseId + "/path" + i % 3 + "/to" + i % 2 + "/obj" + i;
            URI objUri = URI.create(txLocation + "/" + objId);

            try (FcrepoResponse resp = fcrepoClient.put(objUri).perform()) {
                assertEquals(HttpStatus.SC_CREATED, resp.getStatusCode());
            }
        }

        for (final String txLocation : txIds) {
            Runnable commitThread = new Runnable() {
                @Override
                public void run() {
                    URI commitUri = URI.create(txLocation + "/fcr:tx/fcr:commit");
                    try (FcrepoResponse resp = fcrepoClient.post(commitUri).perform()) {
                        assertEquals(HttpStatus.SC_NO_CONTENT, resp.getStatusCode());
                    } catch (IOException | FcrepoOperationFailedException e) {
                        throw new RuntimeException(e);
                    }
                }
            };
            Thread thread = new Thread(commitThread);
            threads.add(thread);
        }

        for (Thread thread : threads) {
            thread.start();
        }

        for (Thread thread : threads) {
            thread.join();
        }
    }

}
