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

import static org.apache.jena.rdf.model.ResourceFactory.createProperty;
import static org.apache.jena.rdf.model.ResourceFactory.createResource;
import static org.slf4j.LoggerFactory.getLogger;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.concurrent.Callable;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.slf4j.Logger;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.HelpCommand;

/**
 * @author bbpennel
 */
@Command(subcommands = {
        HelpCommand.class,
        RepairIssuesCommand.class,
        LocateIssuesCommand.class
    })
public class RepairCLI implements Callable<Integer> {
    private static final Logger output = getLogger("output");
    public static final Property ldp_contains = createProperty( "http://www.w3.org/ns/ldp#contains" );
    public static final Resource NonRdfSource = createResource("http://www.w3.org/ns/ldp#NonRDFSource");
    public static final URI BINARY_TYPE_URI = URI.create(NonRdfSource.getURI());

    protected RepairCLI() {
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new RepairCLI()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {
        output.info("Fedora Sibling Repair utility");
        return 0;
    }

    public static Model createModel(InputStream inStream) {
        try (InputStream stream = inStream) {
            Model model = ModelFactory.createDefaultModel();
            model.read(inStream, null, "TURTLE");
            return model;
        } catch (IOException e) {
            throw new RuntimeException("Failed to close model stream", e);
        }
    }
}
