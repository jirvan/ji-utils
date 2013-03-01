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

import com.jirvan.lang.*;

import java.util.*;

public class JobPool {

    private Map<Long, Job> currentJobs = new HashMap<Long, Job>();
    private int mostRecentJobId = 0;


    public Job startNewJobForTask(Task task) {
        Job job = new Job(++mostRecentJobId);
        java.lang.Runnable runnable = new JobRunnable(job, task);
        new Thread(runnable).start();
        currentJobs.put(job.getJobId(), job);
        return job;
    }

    public Job getJob(long jobId) {
        return currentJobs.get(jobId);
    }

    public static abstract class Task {

        protected StringBuffer outputBuffer;

        public abstract void perform();

        public void output(String string, Object... args) {
            outputBuffer.append(String.format(string, args));
        }

        public void outputLine(String string, Object... args) {
            if (outputBuffer.length() > 0) {
                outputBuffer.append('\n');
            }
            outputBuffer.append(String.format(string, args));
        }

    }

    public static class Job {

        private long jobId;
        private Status status;
        private StringBuffer logBuffer;

        public Job(long jobId) {
            this.jobId = jobId;
            this.status = Status.inProgress;
            this.logBuffer = new StringBuffer();
        }

        public static enum Status {

            inProgress,
            finishedSuccessfully,
            finishedWithError;

        }

        public long getJobId() {
            return jobId;
        }

        public Status getStatus() {
            return status;
        }

        public void setStatus(Status status) {
            this.status = status;
        }

        public String getLog() {
            return logBuffer.toString();
        }

    }

    private static class JobRunnable implements Runnable {
        private Job job;
        private Task task;

        public JobRunnable(Job job, Task task) {
            this.job = job;
            this.task = task;
            task.outputBuffer = job.logBuffer;
        }

        public void run() {
            try {
                task.perform();
                job.setStatus(Job.Status.finishedSuccessfully);
            } catch (Throwable t) {
                job.setStatus(Job.Status.finishedWithError);
                if (job.logBuffer.length() > 0) {
                    job.logBuffer.append('\n');
                }
                if (t instanceof MessageException) {
                    job.logBuffer.append("\nJob finished with ERROR: ");
                    job.logBuffer.append(t.getMessage());
                } else {
                    job.logBuffer.append("\nJob finished with ERROR\n\n");
                    job.logBuffer.append(Utl.getStackTrace(t));
                }
            }
        }

    }

}
