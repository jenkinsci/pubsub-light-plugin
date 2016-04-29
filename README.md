A simple [Publish-Subscribe](http://www.enterpriseintegrationpatterns.com/patterns/messaging/PublishSubscribeChannel.html) event bus module for Jenkins.

Contains the `org.jenkins.pubsub.PubsubBus` abstract class, which is a Jenkins `ExtensionPoint`, with a default
implementation based on [Google's Guava EventBus](https://github.com/google/guava/wiki/EventBusExplained).

# API

Please [see the online Javadoc](http://tfennelly.github.io/jenkins-pubsub-bus-module/) for how to use the API.