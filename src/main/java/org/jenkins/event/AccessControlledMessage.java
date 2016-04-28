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
package org.jenkins.event;

import hudson.security.ACL;
import hudson.security.AccessControlled;
import hudson.security.Permission;
import jenkins.model.Jenkins;
import org.acegisecurity.AccessDeniedException;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

/**
 * {@link AccessControlled} {@link MessageBus} message instance.
 *
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
abstract class AccessControlledMessage extends Message implements AccessControlled {
    
    /**
     * Create a plain message instance.
     */
    AccessControlledMessage() {
    }

    /**
     * Get the permission required to see the message.
     * @return The permission required to see the message.
     */
    protected abstract @Nonnull Permission getRequiredPermission();
    
    /**
     * Get the Jenkins {@link AccessControlled} object associated with this message.
     * @return The Jenkins {@link AccessControlled} object associated with this message,
     * or {code null} if the message is not associated with a
     * Jenkins {@link AccessControlled}.
     */
    protected abstract @CheckForNull AccessControlled getAccessControlled();

    /**
     * {@inheritDoc}
     */
    public @Nonnull
    ACL getACL() {
        AccessControlled eventItem = getAccessControlled();
        if (eventItem != null) {
            return eventItem.getACL();
        } else {
            // TODO: Is the right thing to do?
            return Jenkins.getInstance().getAuthorizationStrategy().getRootACL();
        }
    }

    /**
     * {@inheritDoc}
     */
    public void checkPermission(@Nonnull Permission permission) throws AccessDeniedException {
        if (isUnknownToJenkins()) {
            throw new AccessDeniedException(String.format("Jenkins Item '%s' Unknown.", getName()));
        }
        getACL().checkPermission(permission);
    }
    
    /**
     * {@inheritDoc}
     */
    public boolean hasPermission(@Nonnull Permission permission) {
        if (isUnknownToJenkins()) {
            return false;
        }
        return getACL().hasPermission(permission);
    }

    private boolean isUnknownToJenkins() {
        // If the lookup of that item failed, then it's an unknown Item message.
        return (getAccessControlled() == null);
    }
}
