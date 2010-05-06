/*
 * Copyright (c) 2010-2010 the original author or authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.codehaus.larex.io;

/**
 * <p>Enumerates the streams of a channel.</p>
 *
 * @version $Revision$ $Date$
 */
public enum StreamType
{
    /**
     * <p>The input stream of a channel.</p>
     */
    INPUT,

    /**
     * <p>The output stream of a channel.</p>
     */
    OUTPUT,

    /**
     * <p>Both streams of a channel.</p>
     */
    INPUT_OUTPUT
}
