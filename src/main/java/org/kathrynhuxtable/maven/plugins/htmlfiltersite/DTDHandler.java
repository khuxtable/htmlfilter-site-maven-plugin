/*
 * Copyright (c) 2009 Kathryn Huxtable
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * $Id$
 */
package org.kathrynhuxtable.maven.plugins.htmlfiltersite;

import java.io.IOException;

import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * Custom DTDHandler since for some reason w3c throws exceptions.
 */
public class DTDHandler implements EntityResolver {

    /**
     * @see org.xml.sax.EntityResolver#resolveEntity(java.lang.String, java.lang.String)
     */
    public InputSource resolveEntity(String publicId, String systemId) throws SAXException, IOException {
        if (publicId.equals("-//W3C//DTD XHTML 1.0 Strict//EN")) {
            return inputSource("/xhtml/dtd/xhtml1-strict.dtd");
        } else if (publicId.equals("-//W3C//DTD XHTML 1.0 Transitional//EN")) {
            return inputSource("/xhtml/dtd/xhtml1-transitional.dtd");
        } else if (publicId.equals("-//W3C//DTD XHTML 1.0 Frameset//EN")) {
            return inputSource("/xhtml/dtd/xhtml1-frameset.dtd");
        } else if (publicId.equals("-//W3C//ENTITIES Latin 1 for XHTML//EN")) {
            return inputSource("/xhtml/dtd/xhtml-lat1.ent");
        } else if (publicId.equals("-//W3C//ENTITIES Symbols for XHTML//EN")) {
            return inputSource("/xhtml/dtd/xhtml-symbol.ent");
        } else if (publicId.equals("-//W3C//ENTITIES Special for XHTML//EN")) {
            return inputSource("/xhtml/dtd/xhtml-special.ent");
        } else {
            return null;
        }
    }

    /**
     * DOCUMENT ME!
     *
     * @param  resource DOCUMENT ME!
     *
     * @return DOCUMENT ME!
     */
    private InputSource inputSource(String resource) {
        return new InputSource(DTDHandler.class.getResourceAsStream(resource));
    }
}
