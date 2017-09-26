package org.jenkinsci.plugins.pubsub.message;

public class SimpleJenkinsMessage extends JenkinsMessage<SimpleJenkinsMessage> {
    public SimpleJenkinsMessage() {
    }

    @Override
    public Message clone() {
        Message clone = new SimpleJenkinsMessage();
        clone.putAll(this);
        return clone;
    }
}