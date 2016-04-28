A simple [Message Bus](http://www.enterpriseintegrationpatterns.com/patterns/messaging/MessageBus.html) module for Jenkins.

Contains the `org.jenkins.event.MessageBus` abstract class, which is a Jenkins `ExtensionPoint`, with a default
implementation based on [Google's Guava EventBus](https://github.com/google/guava/wiki/EventBusExplained).

# Publishing to an event Channel

```java
MessageBus bus = MessageBus.getBus();

jobPublisher.publish(new RunMessage(run));
```

# Subscribing to an event Channel

```java
MessageBus bus = MessageBus.getBus();

bus.subscribe(RunMessage.CHANNEL_NAME, new ChannelSubscriber() {
        @Override
        public void onMessage(@Nonnull Message message) {
            if (message instanceof RunMessage) {
                RunMessage jobMessage = (RunMessage)message;
                String jobName = jobMessage.getJobName();
                String runId = message.getId();
                
                // etc etc
            }
        }
    }, 
    User.current(), // Used for authentication 
    null);          // Event filter (none here)
```