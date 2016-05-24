A light-weight [Publish-Subscribe](http://www.enterpriseintegrationpatterns.com/patterns/messaging/PublishSubscribeChannel.html) (async) event notification module for Jenkins.

Contains the `org.jenkins.pubsub.PubsubBus` abstract class, which is a Jenkins `ExtensionPoint`, with a default
implementation based on [Google's Guava EventBus](https://github.com/google/guava/wiki/EventBusExplained).

# API

Please [see the online Javadoc](http://jenkinsci.github.io/pubsub-light-module/) for how to use the API.

# This is not a full-blown Event Bus
 
The guiding design principal of this module is:

* Light-weight, untyped, name-value pair messages containing "just enough" information to allow an event subscriber to decide if they are interested in getting all of the data associated with the event (typically via a REST API call).

This was never intended to be a full-blown Enterprise Service Bus, with complex message types, complex message routing, complex message transformation etc.
It was created to provide light-weight async event notification for the SSE Gateway plugin. 