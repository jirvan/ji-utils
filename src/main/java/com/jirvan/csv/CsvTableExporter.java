/*

Copyright (c) 2006,2007,2008,2009,2010,2011,2012,2013 Jirvan Pty Ltd
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

import com.jirvan.lang.*;

import javax.sql.*;
import java.io.*;
import java.math.*;
import java.sql.*;
import java.text.*;

public class CsvTableExporter {

    private static DateFormat timestampFormat = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss");

    public static long exportToFile(DataSource dataSource, String tableName, String sql, String whereClauseCondition, File outFile) {
        try {
            Connection connection = dataSource.getConnection();
            try {
                return exportToFile(connection, tableName, sql, whereClauseCondition, outFile);
            } finally {
                connection.close();
            }
        } catch (SQLException e) {
            throw new SQLRuntimeException(e);
        }
    }

    public static long exportToFile(Connection connection, String tableName, String sql, String whereClauseCondition, File outFile) {
        if (sql == null || sql.trim().length() == 0) {
            sql = whereClauseCondition == null || whereClauseCondition.trim().length() == 0
                  ? "select * from " + tableName
                  : "select * from " + tableName + " where " + whereClauseCondition;
        }
        try {
            PreparedStatement stmt = connection.prepareStatement(sql);
            try {
                ResultSet rset = stmt.executeQuery();
                try {
                    FileWriter writer = new FileWriter(outFile);
                    try {

                        // Write the header
                        int columnCount = rset.getMetaData().getColumnCount();
                        for (int i = 0; i < columnCount; i++) {
                            if (i != 0) writer.write(",");
                            writer.write(rset.getMetaData().getColumnName(i + 1));
                        }
                        writer.write("\n");

                        // Write the data lines
                        long rowsExported = 0;
                        while (rset.next()) {
                            for (int i = 0; i < columnCount; i++) {
                                if (i != 0) writer.write(",");
                                writer.write(formatValue(rset.getObject(i + 1)));
                            }
                            writer.write("\n");
                            rowsExported++;
                        }

                        return rowsExported;

                    } finally {
                        writer.close();
                    }
                } finally {
                    stmt.close();
                }
            } finally {
                stmt.close();
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (SQLException e) {
            throw new SQLRuntimeException(e, sql);
        }
    }

    private static String formatValue(Object value) throws SQLException {
        if (value == null) {
            return "";
        } else if (value instanceof Timestamp) {
            return timestampFormat.format((Timestamp) value);
        } else if (value instanceof String) {
            if (((String) value).indexOf('"') == -1 && ((String) value).indexOf(',') == -1) {
                return ((String) value).replaceAll("\"", "\"\"");
            } else {
                return "\"" + ((String) value).replaceAll("\"", "\"\"") + "\"";
            }
        } else if (value instanceof BigDecimal) {
            return value.toString().replaceFirst("\\.0+$", "").replaceFirst("\\.(\\d+)0+$", ".$1");
        } else {
            return value.toString();
        }
    }

}