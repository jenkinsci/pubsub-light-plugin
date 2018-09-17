/*
 * The MIT License
 *
 * Copyright (c) 2016, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.jenkinsci.plugins.pubsub;

/**
 * Pre-defined event property name enumerations.
 * <p>
 * Of course new event property names (not pre-defined here) can be created/used.
 * The idea of types pre-defined here is to try help standardise on the event
 * property names used.
 * <p>
 * If you find yourself needing a new event property names, consider
 * creating a Pull Request on this repo, adding it as one of the pre-defined
 * event property names.
 * <p>
 * <strong>*** SEE the docs on the nested types for more details ***</strong>
 *  
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 * @see Events
 */
public interface EventProps {

    /**
     * Pre-defined Jenkins/core event property names.
     */
    enum Jenkins {
        /**
         * The Jenkins organization origin of the event.
         */
        jenkins_org,
        /**
         * The ID of the Jenkins instance that published the message.
         */
        jenkins_instance_id,
        /**
         * The URL of the Jenkins instance that published the message.
         */
        jenkins_instance_url,
        /**
         * The event channel name on which the message was sent.
         */
        jenkins_channel,
        /**
         * The event name. See {@link Events} for pre-defined types.
         */
        jenkins_event,
        /**
         * The millisecond timestamp for when the event happened.
         * <p>
         * Of course, this does not preclude the event message from containing other
         * timestamp event properties, where appropriate.
         */
        jenkins_event_timestamp,
        /**
         * The event UUID.
         */
        jenkins_event_uuid,
        /**
         * Jenkins domain object type.
         */
        jenkins_object_type,
        /**
         * Jenkins domain object full name.
         */
        jenkins_object_name,
        /**
         * Jenkins domain object unique ID.
         */
        jenkins_object_id,
        /**
         * Jenkins domain object URL.
         */
        jenkins_object_url,
    }

    /**
     * Pre-defined Job channel event property names.
     * <ul>
     *     <li>See {@link Events.JobChannel} for Job channel events.</li>
     *     <li>See {@link Jenkins} for core properties common to most/all messages.</li>
     * </ul>
     */
    enum Job {
        /**
         * Job name.
         */
        job_name,
        /**
         * Job is a multi-branch job.
         * <p>
         * "true" if it is a multi-branch job, otherwise it is not a multi-branch job.
         */
        job_ismultibranch,
        /**
         * Multi-branch job indexing status.
         */
        job_multibranch_indexing_status,
        /**
         * Multi-branch job indexing result.
         */
        job_multibranch_indexing_result,
        /**
         * Job run Queue Id.
         * <p>
         * This is how you correlate between Job Queue tasks and the Runs
         * that result from them.
         */
        job_run_queueId,
        /**
         * Job run status/result.
         */
        job_run_status,
        /**
         * Job run SCM commit Id, if relevant.
         */
        job_run_commitId,
        /**
         * Causes of this run.
         */
        job_run_causes
    }
    
    /**
     * Pre-defined {@link Item} event property names.
     * <ul>
     *     <li>See {@link Jenkins} for core properties common to most/all messages.</li>
     * </ul>
     */
    enum Item {
        
        /**
         * Item (e.g. a Job) name before a rename operation.
         */
        item_rename_before,
        
        /**
         * Item (e.g. a Job) name after a rename operation.
         */
        item_rename_after,
    }
}
