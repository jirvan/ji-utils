/*

Copyright (c) 2014, Jirvan Pty Ltd
All rights reserved.

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

    * Redistributions of source code must retain the above copyright notice,
      this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above copyright notice,
      this list of conditions and the following disclaimer in the documentation
      and/or other materials provided with the distribution.
    * Neither the name of Jirvan Pty Ltd nor the names of its contributors
      may be used to endorse or promote products derived from this software
      without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

*/

package com.jirvan.csv;

import au.com.bytecode.opencsv.CSVReader;
import com.jirvan.lang.MessageException;
import com.jirvan.lang.SQLRuntimeException;
import com.jirvan.util.Strings;
import org.apache.commons.io.FileUtils;

import javax.sql.DataSource;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static com.jirvan.util.Assertions.*;
import static com.jirvan.util.Strings.*;

public class BaseCsvImporter {

    private LinkedHashMap<String, CsvFileImporter> csvFileImporterMap;

    protected BaseCsvImporter(CsvFileImporter... csvFileImporters) {
        assertTrue(csvFileImporters.length > 0, "At least one CsvFileImporter must be provided");
        this.csvFileImporterMap = new LinkedHashMap<>();
        for (CsvFileImporter csvFileImporter : csvFileImporters) {
            this.csvFileImporterMap.put(csvFileImporter.handlesFileWithName(), csvFileImporter);
        }
    }

    public void importFromZipFile(DataSource dataSource, File zipFile) {
        try (InputStream zipInputStream = new FileInputStream(zipFile)) {
            importFromZippedInputStream(dataSource, zipInputStream);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void importFromZippedInputStream(DataSource dataSource, InputStream zippedInputStream) {
        try {
            File tempDir = Files.createTempDirectory("BaseCsvImporter.importDataEtc").toFile();
            try {

                // Extract zippedInputStream to temp data directory
                ZipInputStream zipInputStream = new ZipInputStream(zippedInputStream);
                ZipEntry zipEntry;
                while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                    if (!(string(zipEntry.getName()).isIn(".DS_Store", ".localized")
                          || zipEntry.getName().startsWith("__MACOSX"))) {
                        FileOutputStream fileOutputStream = new FileOutputStream(new File(tempDir, zipEntry.getName()));
                        try {
                            int len;
                            byte[] buffer = new byte[1024];
                            while ((len = zipInputStream.read(buffer)) > 0) {
                                fileOutputStream.write(buffer, 0, len);
                            }
                        } finally {
                            fileOutputStream.close();
                        }
                    }
                }

                // Import from the temp data directory
                importFromDataDirectory(dataSource, tempDir);

            } finally {
                FileUtils.deleteDirectory(tempDir);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void importFromDataDirectory(DataSource dataSource, File dataDir) {

        checkDataDirectory(dataDir);
        StringBuilder pendingOutput = new StringBuilder();
        try {
            Connection connection = dataSource.getConnection();
            connection.setAutoCommit(false);
            try {

                System.out.printf("Importing data\n");
                System.out.flush();
                for (Map.Entry<String, CsvFileImporter> entry : csvFileImporterMap.entrySet()) {
                    File csvFile = new File(dataDir, entry.getKey());
                    CsvFileImporter csvFileImporter = entry.getValue();
                    importFromCsvFile(pendingOutput,
                                      connection,
                                      csvFileImporter,
                                      csvFile);
                }

                connection.commit();
                connection.close();
            } catch (Throwable t) {
                connection.rollback();
                connection.close();
                throw t;
            }
        } catch (SQLException e) {
            throw new SQLRuntimeException(e);
        }
        System.out.printf("%sFinished importing data\n\n", pendingOutput.toString());

    }

    private void importFromCsvFile(StringBuilder pendingOutput, Connection connection, CsvFileImporter csvFileImporter, File csvFile) {
        long rows = 0;
        try {
            if (csvFileImporter instanceof SimpleCsvFileImporter) {
                rows = ((SimpleCsvFileImporter) csvFileImporter).importFromCsvFile(connection, csvFile);
            } else if (csvFileImporter instanceof LineBasedCsvFileImporter) {
                rows = importFromReader(connection, (LineBasedCsvFileImporter) csvFileImporter, new FileReader(csvFile));
            } else {
                throw new RuntimeException(String.format("Unsupported CsvFileImporter sub type \"%s\"", csvFileImporter.getClass().getName()));
            }
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
        pendingOutput.append(String.format("  - processed %d rows from %s\n", rows, csvFile.getName()));
    }

    private static long importFromReader(Connection connection,
                                         LineBasedCsvFileImporter csvFileImporter,
                                         Reader reader) {
        assertNotNull(connection, "connection is null");
        try {

            CSVReader csvReader = new CSVReader(reader);
            int lineNumber = 1;
            try {

                // Get the column names
                String[] columnNames = csvReader.readNext();
                if (columnNames == null || columnNames.length == 0) {
                    throw new RuntimeException("First row is empty (expected column names");
                }

                // Prepare the insert statement and execute it for each row in the csv file
                String[] nextLine;
                while ((nextLine = csvReader.readNext()) != null) {
                    lineNumber++;

                    if (nextLine.length == 1 && (nextLine[0] == null || nextLine[0].trim().length() == 0)) {
                        continue;
                    }

                    try {

                        // Check the number of fields is correct
                        if (nextLine.length != columnNames.length) {
                            throw new RuntimeException("Exception processing line " + lineNumber + ": This line has "
                                                       + nextLine.length + " fields, but " + columnNames.length
                                                       + " (the number of headings in the first line) were expected.");
                        }
                        csvFileImporter.processCsvLine(connection, nextLine);

                    } catch (Throwable t) {
                        throw CsvLineRuntimeException.wrapIfAppropriate(lineNumber, t);
                    }

                }

            } finally {
                csvReader.close();
            }

            return lineNumber - 1;

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    protected interface CsvFileImporter {

        public String handlesFileWithName();

//        public void importFromCsvFile(StringBuilder pendingOutput, Connection connection, File csvFile);

    }

    protected interface LineBasedCsvFileImporter extends CsvFileImporter {

        public void processCsvLine(Connection connection, String[] nextLine);

    }

    public static class SimpleCsvFileImporter implements CsvFileImporter {

        private String handlesFilesWithName;

        public SimpleCsvFileImporter(String handlesFilesWithName) {
            this.handlesFilesWithName = handlesFilesWithName;
        }

        public String handlesFileWithName() {
            return handlesFilesWithName;
        }

        public long importFromCsvFile(Connection connection, File csvFile) {
            String tableName = csvFile.getName().replaceFirst("(?i)\\.csv$", "");
            return CsvTableImporter.importFromFile(connection, tableName, csvFile);
        }
    }

    private void checkDataDirectory(File dataDir) {

        // Check directory exists
        assertNotNull(dataDir, "Data directory must not be null");
        try {
            assertFileExists(dataDir, String.format("Data directory \"%s\" does not exist", dataDir.getAbsolutePath()));
            assertIsDirectory(dataDir, String.format("\"%s\" is not a directory", dataDir.getAbsolutePath()));
        } catch (AssertionError e) {
            throw new MessageException(e.getMessage());
        }

        // Check directory contains the expected files (and no others)
        Set<String> csvFileNames = csvFileImporterMap.keySet();
        String[] filesPresent = dataDir.list();
        for (String filePresent : filesPresent) {
            if (!Strings.isIn(filePresent, csvFileNames)) {
                throw new MessageException(String.format("File \"%s\" is unexpected\n(expected one of: %s)",
                                                         filePresent, Strings.commaSpaceList(csvFileNames)));
            }
        }
        for (String expectedFile : csvFileNames) {
            if (!Strings.isIn(expectedFile, filesPresent)) {
                throw new MessageException(String.format("\"%s\" is missing", expectedFile));
            }
        }

    }

}