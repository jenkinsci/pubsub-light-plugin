A simple [Message Bus](http://www.enterpriseintegrationpatterns.com/patterns/messaging/MessageBus.html) module for Jenkins.

Contains the `org.jenkins.event.MessageBus` abstract class, which is a Jenkins `ExtensionPoint`, with a default
implementation based on [Google's Guava EventBus](https://github.com/google/guava/wiki/EventBusExplained).

# Publishing to an event Channel

```java
MessageBus bus = MessageBus.getBus();
ChannelPublisher jobPublisher = bus.newPublisher("jenkins.job");

jobPublisher.publish(new RunMessage(run));
```

# Subscribing to an event Channel

```java
MessageBus bus = MessageBus.getBus();

bus.subscribe("jenkins.job", new ChannelSubscriber() {
    @Override
    public void onMessage(@Nonnull Message message) {
        if (message instanceof JobMessage) {
            JobMessage jobMessage = (JobMessage)message;
            String jobName = jobMessage.getJobName();
            
            if (message instanceof RunMessage) {
                String runId = message.getId();
                
                // etc etc
            }
        }
    }
}, User.current(), null);
```