/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.asterix.external.reader;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.asterix.external.api.AsterixInputStream;
import org.apache.asterix.external.input.record.reader.stream.StreamRecordReader;
import org.apache.asterix.external.util.ExternalDataConstants;
import org.apache.hyracks.api.exceptions.HyracksDataException;

public class CAPMessageRecordReader extends StreamRecordReader {

    private int curLvl;
    private int recordLvl;
    private boolean recordContentFlag;

    private static final List<String> recordReaderFormats = Collections.unmodifiableList(Arrays.asList("cap"));
    private static final String REQUIRED_CONFIGS = "";

    private enum CAPParserState {
        INIT_STATE,
        START_OF_ELEMENT_NAME,
        END_OF_ELEMENT_NAME,
        START_OF_PROLOG,
        IN_START_OF_ELEMENT_NAME,
        IN_END_OF_ELEMENT_NAME,
        IN_PROLOG,
        IN_SCHEMA_DEFINITION
    }

    @Override
    public boolean hasNext() throws IOException {
        boolean newRecordFormed = false;

        record.reset();
        int startPos = bufferPosn;
        CAPParserState state = CAPParserState.INIT_STATE;
        do {
            // chk whether there is enough data in buffer
            if (bufferPosn >= bufferLength) {
                if (recordContentFlag) {
                    if (startPos == -1) {
                        char[] tempBuffer = ("<" + String.valueOf(inputBuffer)).toCharArray();
                        record.append(tempBuffer, 0, bufferPosn - startPos);
                    } else {
                        record.append(inputBuffer, startPos, bufferPosn - startPos);
                    }
                }
                startPos = bufferPosn = 0;
                bufferLength = reader.read(inputBuffer);
                if (bufferLength < 0) {
                    close();
                    return false;
                }
            }
            // TODO: simplify the state numbers (xikui)
            switch (inputBuffer[bufferPosn]) {
                case '<':
                    state = CAPParserState.START_OF_ELEMENT_NAME;
                    break;
                case '/':
                    if (state == CAPParserState.START_OF_ELEMENT_NAME) {
                        state = CAPParserState.END_OF_ELEMENT_NAME;
                    }
                    break;
                case '>':
                    switch (state) {
                        case IN_START_OF_ELEMENT_NAME:
                            curLvl++;
                            break;
                        case IN_END_OF_ELEMENT_NAME:
                            curLvl--;
                            break;
                        case IN_SCHEMA_DEFINITION:
                            //schema definition, do nothing
                            break;
                        default:
                            //do nothing
                    }
                    if (curLvl == recordLvl && state == CAPParserState.IN_END_OF_ELEMENT_NAME) {
                        int appendLength = bufferPosn + 1 - startPos;
                        record.append(inputBuffer, startPos, appendLength);
                        record.endRecord();
                        newRecordFormed = true;
                        recordContentFlag = false;
                    }
                    state = CAPParserState.INIT_STATE;
                    break;
                case '?':
                    if (state == CAPParserState.START_OF_ELEMENT_NAME) {
                        state = CAPParserState.START_OF_PROLOG;
                    } else if (state == CAPParserState.IN_PROLOG) {
                        // in document head, do nothing.
                    }
                    break;
                case '!':
                    if (state == CAPParserState.START_OF_ELEMENT_NAME) {
                        state = CAPParserState.IN_SCHEMA_DEFINITION; // in schema definition
                    }
                    break;
                default:
                    switch (state) {
                        case START_OF_ELEMENT_NAME:
                            if (curLvl == recordLvl) {
                                startPos = bufferPosn - 1;
                                recordContentFlag = true;
                            }
                            state = CAPParserState.IN_START_OF_ELEMENT_NAME; // in start element name
                            break;
                        case END_OF_ELEMENT_NAME:
                            state = CAPParserState.IN_END_OF_ELEMENT_NAME; // in end element name
                            break;
                        case START_OF_PROLOG:
                            state = CAPParserState.IN_PROLOG; // inside document head
                            break;
                        default:
                            // do nothing
                    }
            }
            bufferPosn++;
        } while (!newRecordFormed);
        return true;
    }

    @Override
    public List<String> getRecordReaderFormats() {
        return recordReaderFormats;
    }

    @Override
    public String getRequiredConfigs() {
        return REQUIRED_CONFIGS;
    }

    @Override
    public void configure(AsterixInputStream inputStream, Map<String, String> config) throws HyracksDataException {
        super.configure(inputStream);
        String collection = config.get("collection");
        bufferPosn = 0;
        curLvl = 0;
        recordContentFlag = false;

        if (collection != null) {
            this.recordLvl = Boolean.parseBoolean(collection) ? 1 : 0;
        } else {
            this.recordLvl = 0;
        }
    }
}
