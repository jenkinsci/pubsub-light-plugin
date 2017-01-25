A light-weight [Publish-Subscribe](http://www.enterpriseintegrationpatterns.com/patterns/messaging/PublishSubscribeChannel.html) (async) event notification module for Jenkins.

Contains the `org.jenkins.pubsub.PubsubBus` abstract class, which is a Jenkins `ExtensionPoint`, with a default
implementation based on [Google's Guava EventBus](https://github.com/google/guava/wiki/EventBusExplained).

> __Note__: Other implementations can be created for other Messaging Systems e.g. a JMS based implementation for a distributed bus. An alternative to a distributed bus might be a [Messaging Bridge](http://www.enterpriseintegrationpatterns.com/patterns/messaging/MessagingBridge.html), with a Jenkins master instance using the default bus implementation internally, while "sharing" events globally via a bridge that just listens in on the bus and republishes the events.

# API

Please [see the online Javadoc](http://jenkinsci.github.io/pubsub-light-module/) for how to use the API.

# This is not a full-blown Event Bus
 
The guiding design principal of this module is:

* Light-weight, untyped, name-value pair messages containing "just enough" information to allow an event subscriber to decide if they are interested in getting all of the data associated with the event (typically via a REST API call).

This was never intended to be a full-blown Enterprise Service Bus, with complex message types, complex message routing, complex message transformation etc.
 It was created to provide light-weight async event notification for the SSE Gateway plugin.
 
# FAQs

Some questions that have been asked (or are anticipated :smile: ).

### Why not just put everything into the event payload?

This is a fair question to ask; if the lightweight event just results in a REST API call to get more data, then why not short-circuit the process and put all the data into the event before publishing?
  
We feel that there's no "right" answer to the question of whether we should go with light or heavy events. Different use cases will have different requirements. Some will want less data than others. Some will want completely different data i.e. just using the event as a trigger, not using any of the event data.

If we had gone with heavier events, it is guaranteed that at some point in time, people would encounter situations where heavy events are being published and only one or two fields from those events are being used. Guess what the obvious question from these people would be? So in reality, there's no "right" answer to this question, but on balance, we feel that the lightweight message approach will work out to be better.

> Note, your application can also implement [`MessageEnricher`](http://jenkinsci.github.io/pubsub-light-module/org/jenkinsci/plugins/pubsub/MessageEnricher.html) extension points to add data to the default payloads. This could be used as a way to "bulk up" events with more data that you know will be used, saving follow-up REST calls etc. Please use with a clear head !! 