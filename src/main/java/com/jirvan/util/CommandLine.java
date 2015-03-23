/*

Copyright (c) 2013, Jirvan Pty Ltd
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

package com.jirvan.util;

import com.jirvan.io.OutputWriter;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.jirvan.util.Assertions.*;

/**
 * Used to execute commands on the native command line
 *
 * @see @AbstractCommandLineProcessor
 */
public class CommandLine {

    private String command;
    private ProcessBuilder processBuilder;
    private OutputWriter outputWriter;

    public CommandLine(String command, String... arguments) {
        assertNotNull(command, "command is null");
        this.command = command;
        List<String> commandAndArguments = Arrays.asList(arguments);
        commandAndArguments.add(0, command);
        processBuilder = new ProcessBuilder(commandAndArguments);
    }

    public CommandLine changeEnvironment(EnvironmentVariableChanger environmentVariableChanger) {
        environmentVariableChanger.change(processBuilder.environment());
        return this;
    }

    public CommandLine setDirectory(File directory) {
        processBuilder.directory(directory);
        return this;
    }

    public CommandLine setOutputWriter(OutputWriter outputWriter) {
        this.outputWriter = outputWriter;
        return this;
    }

    public static void execute(OutputWriter outputWriter, String command, String... arguments) {
        new CommandLine(command, arguments)
                .setOutputWriter(outputWriter)
                .execute();
    }

    public void execute() {
        try {
            if (outputWriter != null) {
                processBuilder.redirectErrorStream(true);
                processBuilder.redirectOutput(ProcessBuilder.Redirect.PIPE);
                Process proc = processBuilder.start();
                BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream()));
                String line = null;
                while ((line = br.readLine()) != null) {
                    outputWriter.printf(line);
                    outputWriter.printf("\n");
                }
                if (proc.waitFor() != 0) {
                    throw new RuntimeException(String.format("Error executing \"%s\"", command));
                }
            } else {
                processBuilder.redirectError(ProcessBuilder.Redirect.INHERIT);
                processBuilder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
                Process proc = processBuilder.start();
                if (proc.waitFor() != 0) {
                    throw new RuntimeException(String.format("Error executing \"%s\"", command));
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

    }

    public static abstract class EnvironmentVariableChanger {
        public abstract void change(Map<String, String> env);
    }

}
