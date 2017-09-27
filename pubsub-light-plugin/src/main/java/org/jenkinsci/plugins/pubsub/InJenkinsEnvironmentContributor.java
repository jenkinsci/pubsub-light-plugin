package org.jenkinsci.plugins.pubsub;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.EnvironmentContributor;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TaskListener;

import javax.annotation.Nonnull;
import java.io.IOException;

/**
 * Used to indicate to other components in this plugin that they are running in the context of a Jenkins instance.
 */
@Extension
public class InJenkinsEnvironmentContributor extends EnvironmentContributor {
    @Override
    public void buildEnvironmentFor(@Nonnull final Run r, @Nonnull final EnvVars envs, @Nonnull final TaskListener listener) throws IOException, InterruptedException {
        envs.put("IN_JENKINS", "true");
    }

    @Override
    public void buildEnvironmentFor(@Nonnull final Job j, @Nonnull final EnvVars envs, @Nonnull final TaskListener listener) throws IOException, InterruptedException {
        envs.put("IN_JENKINS", "true");
    }
}
