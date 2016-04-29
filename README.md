A simple [Publish-Subscribe](http://www.enterpriseintegrationpatterns.com/patterns/messaging/PublishSubscribeChannel.html) event bus module for Jenkins.

Contains the `org.jenkins.pubsub.PubsubBus` abstract class, which is a Jenkins `ExtensionPoint`, with a default
implementation based on [Google's Guava EventBus](https://github.com/google/guava/wiki/EventBusExplained).

# Publishing to an event Channel

```java
PubsubBus bus = PubsubBus.getBus();

bus.publish(new RunMessage(run)
            .setEventName("run.started"));
```

# Subscribing to an event Channel

```java
PubsubBus bus = PubsubBus.getBus();

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
    new EventFilter().setEventName("run.started")); // Event filter (optional)
```