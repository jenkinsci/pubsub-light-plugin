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
import hudson.model.ItemGroup;
import hudson.model.Job;
import hudson.search.Search;
import hudson.search.SearchIndex;
import hudson.security.ACL;
import hudson.security.Permission;
import org.acegisecurity.AccessDeniedException;
import org.acegisecurity.Authentication;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.util.Collection;

/**
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
public class MockItem implements Item {

    private ACL acl;
    public MockItem() {
        this(MockItem.class.getSimpleName());
    }

    public MockItem(String name) {
        this.name = name;
    }

    public MockItem setACL(ACL acl) {
        this.acl = acl;
        return this;
    }

    private String name;

    public static final ACL YES_ACL = new ACL() {
        @Override
        public boolean hasPermission(@Nonnull Authentication a, @Nonnull Permission permission) {
            return true;
        }
    };
    public static final ACL NO_ACL = new ACL() {
        @Override
        public boolean hasPermission(@Nonnull Authentication a, @Nonnull Permission permission) {
            return false;
        }
    };

    public ItemGroup<? extends Item> getParent() {
        return null;
    }

    public Collection<? extends Job> getAllJobs() {
        return null;
    }

    public String getName() {
        return name;
    }

    public String getFullName() {
        return name;
    }

    public String getDisplayName() {
        return name;
    }

    public String getFullDisplayName() {
        return name;
    }

    public String getRelativeNameFrom(ItemGroup g) {
        return null;
    }

    public String getRelativeNameFrom(Item item) {
        return null;
    }

    public String getUrl() {
        return "http://example.com/jenkins/x";
    }

    public String getShortUrl() {
        return "http://example.com/jenkins/x";
    }

    public String getAbsoluteUrl() {
        return "http://example.com/jenkins/x";
    }

    public void onLoad(ItemGroup<? extends Item> parent, String name) throws IOException {

    }

    public void onCopiedFrom(Item src) {

    }

    public void onCreatedFromScratch() {

    }

    public void save() throws IOException {

    }

    public void delete() throws IOException, InterruptedException {

    }

    public Collection getItems() {
        return null;
    }

    public String getUrlChildPrefix() {
        return null;
    }

    public Item getItem(String name) throws AccessDeniedException {
        return null;
    }

    public File getRootDirFor(Item child) {
        return null;
    }

    public void onRenamed(Item item, String oldName, String newName) throws IOException {

    }

    public void onDeleted(Item item) throws IOException {

    }

    @Nonnull
    public ACL getACL() {
        return acl;
    }

    public void checkPermission(@Nonnull Permission permission) throws AccessDeniedException {

    }

    public boolean hasPermission(@Nonnull Permission permission) {
        return false;
    }

    public File getRootDir() {
        return null;
    }

    public Search getSearch() {
        return null;
    }

    public String getSearchName() {
        return null;
    }

    public String getSearchUrl() {
        return null;
    }

    public SearchIndex getSearchIndex() {
        return null;
    }
}
