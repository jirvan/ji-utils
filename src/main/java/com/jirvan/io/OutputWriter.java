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

package com.jirvan.io;

import org.slf4j.Logger;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class OutputWriter {

    private List<Logger> loggers = new ArrayList<>();
    private List<org.apache.log4j.Logger> log4jLoggers = new ArrayList<>();
    private List<OutputStream> outputStreams = new ArrayList<>();
    private List<Writer> writers = new ArrayList<>();

    public OutputWriter(OutputStream... outputStreams) {
        this.outputStreams.addAll(Arrays.asList(outputStreams));
    }

    public OutputWriter(Logger logger1, Logger... loggers2ToN) {
        add(logger1, loggers2ToN);
    }

    public OutputWriter(org.apache.log4j.Logger logger1, org.apache.log4j.Logger... loggers2ToN) {
        add(logger1, loggers2ToN);
    }

    public OutputWriter(Writer outputWriter1, Writer... outputWriters2ToN) {
        add(outputWriter1, outputWriters2ToN);
    }

    public OutputWriter(OutputWriter outputWriter1, OutputWriter... outputWriters2ToN) {
        add(outputWriter1, outputWriters2ToN);
    }

    public OutputWriter add(OutputWriter outputWriter1, OutputWriter... outputWriters2ToN) {
        this.outputStreams.addAll(outputWriter1.outputStreams);
        for (OutputWriter outputWriter : outputWriters2ToN) {
            this.outputStreams.addAll(outputWriter.outputStreams);
        }
        this.writers.addAll(outputWriter1.writers);
        for (OutputWriter outputWriter : outputWriters2ToN) {
            this.writers.addAll(outputWriter.writers);
        }
        return this;
    }

    public OutputWriter add(Logger logger1, Logger... loggers2ToN) {
        this.loggers.add(logger1);
        this.loggers.addAll(Arrays.asList(loggers2ToN));
        return this;
    }

    public OutputWriter add(org.apache.log4j.Logger logger1, org.apache.log4j.Logger... loggers2ToN) {
        this.log4jLoggers.add(logger1);
        this.log4jLoggers.addAll(Arrays.asList(loggers2ToN));
        return this;
    }

    public OutputWriter add(OutputStream outputStream1, OutputStream... outputStreams2ToN) {
        this.outputStreams.add(outputStream1);
        this.outputStreams.addAll(Arrays.asList(outputStreams2ToN));
        return this;
    }

    public OutputWriter add(Writer outputWriter1, Writer... outputWriters2ToN) {
        this.writers.add(outputWriter1);
        this.writers.addAll(Arrays.asList(outputWriters2ToN));
        return this;
    }

    public OutputWriter printf(String format, Object... args) {
        printToAllOutputs(String.format(format, args));
        return this;
    }

    public OutputWriter printf(Logger additionalLoggerToPrintTo, String format, Object... args) {
        String formattedString = String.format(format, args);
        additionalLoggerToPrintTo.info(formattedString.replaceFirst("\\n$", ""));
//        additionalLoggerToPrintTo.info(formattedString.replaceFirst("^\\n", "").replaceFirst("\\n$", ""));
        printToAllOutputs(formattedString);
        return this;
    }

    public OutputWriter printf(org.apache.log4j.Logger additionalLoggerToPrintTo, String format, Object... args) {
        String formattedString = String.format(format, args);
        additionalLoggerToPrintTo.info(formattedString.replaceFirst("\\n$", ""));
//        additionalLoggerToPrintTo.info(formattedString.replaceFirst("^\\n", "").replaceFirst("\\n$", ""));
        printToAllOutputs(formattedString);
        return this;
    }

    private void printToAllOutputs(String formattedString) {
        try {

            for (Logger logger : loggers) {
                logger.info(formattedString.replaceFirst("^\\n", "").replaceFirst("\\n$", ""));
            }
            for (org.apache.log4j.Logger logger : log4jLoggers) {
                logger.info(formattedString.replaceFirst("^\\n", "").replaceFirst("\\n$", ""));
            }
            for (OutputStream outputStream : outputStreams) {
                outputStream.write(formattedString.getBytes());
                outputStream.flush();
            }
            for (Writer outputWriter : writers) {
                outputWriter.write(formattedString);
                outputWriter.flush();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


}