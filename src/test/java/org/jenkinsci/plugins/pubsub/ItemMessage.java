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

import hudson.model.Item;
import hudson.security.AccessControlled;
import hudson.security.Permission;
import jenkins.model.Jenkins;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

/**
 * Jenkins {@link Item} domain model {@link PubsubBus} message instance.
 *
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
public final class ItemMessage extends AccessControlledMessage<ItemMessage> {
    
    private static final long serialVersionUID = -1L;

    transient Item messageItem;
    private transient boolean messageItemLookupComplete = false;

    /**
     * Create a plain message instance.
     */
    public ItemMessage() {
        super();
    }

    /**
     * Create a message instance associated with a Jenkins {@link Item}.
     * @param messageItem The Jenkins {@link Item} with this message instance
     *                    is to be associated.
     */
    public ItemMessage(@Nonnull Item messageItem) {
        this.messageItem = messageItem;
        set(EventProps.Jenkins.jenkins_object_name, messageItem.getFullName());
        set(EventProps.Jenkins.jenkins_object_url, messageItem.getUrl());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Message clone() {
        Message clone = new ItemMessage();
        clone.putAll(this);
        return clone;
    }

    @Nonnull
    @Override
    protected Permission getRequiredPermission() {
        return Item.READ;
    }

    /**
     * Get the Jenkins {@link Item} assocated with this message.
     * @return The Jenkins {@link Item} assocated with this message,
     * or {code null} if the message is not associated with a
     * Jenkins {@link Item}.
     */
    protected synchronized @CheckForNull AccessControlled getAccessControlled() {
        if (messageItemLookupComplete || messageItem != null) {
            return messageItem;
        }
        
        try {
            Jenkins jenkins = Jenkins.getInstance();

            String itemName = getObjectName();
            if (itemName != null) {
                messageItem = jenkins.getItemByFullName(itemName);
            }
        } finally {
            messageItemLookupComplete = true;
        }
        return messageItem;
    }
}
