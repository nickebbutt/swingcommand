
/*
 * Copyright 2009 Object Definitions Ltd.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package swingcommand;

/**
 * Created by IntelliJ IDEA.
 * User: Nick Ebbutt
 * Date: 09-Sep-2008
 * Time: 14:52:16
 *
 * A Task which runs partly in a background thread
 */
public abstract class BackgroundTask<P,E> extends Task<P,E> {

    protected void doBackgroundProcessing() throws Exception {
        doInBackground();
    }

    /**
    * The Subclass should implement this method to perform the background processing
    * This method is called in a background thread
    */
    protected abstract void doInBackground() throws Exception;
}