# Fedora Repair Siblings Utility
Java commandline utility for repairing resources created in Fedora with same name siblings. This is generally caused by multiple concurrent requests to create resources with matching pairtree components inside of transactions.

### Usage
To build:
`mvn clean install`

To find resources with same name siblings within a container:
`java -jar target/fedora-repair-siblings.jar locate -b http://localhost:8080/fcrepo/rest/content -u fedoraAdmin -p`

Save the results to a file, such as results.txt.

Then repair the issues:
`java -jar target/fedora-repair-siblings.jar repair results.txt -b http://localhost:8080/fcrepo/rest -u fedora_admin -p`