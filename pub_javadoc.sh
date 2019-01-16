#!/bin/bash

mvn javadoc:javadoc

pushd target/site/apidocs

git init
git remote add javadoc git@github.com:jenkinsci/pubsub-light-plugin.git
git fetch --depth=1 javadoc gh-pages

git add --all
git commit -m "javadoc"

git push javadoc master:gh-pages -f

rm -rf .git

popd