/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.chemistry.opencmis.server.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;

import org.apache.chemistry.opencmis.server.impl.webservices.ProtectionRequestWrapper;
import org.junit.Test;

public class CheckServletInputStreamTest {

    private static final String BOUNDARY = "test.boundray";

    @Test
    public void testStream1() {
        baseTest(200, 50, 20, 10, true);
    }

    @Test
    public void testStream2() {
        baseTest(100 * 1024, 10 * 1024, 8 * 1024, 4 * 1024, true);
    }

    @Test
    public void testStream3() {
        baseTest(100 * 1024, 8 * 1024, 10 * 1024, 4 * 1024, false);
    }

    @Test
    public void testStream4() {
        baseTest(100 * 1024, 10, 7, 4 * 1024, true);
    }

    @Test
    public void testStream5() {
        baseTest(100 * 1024, 99 * 1024, 2 * 1024, 11, true);
    }

    @Test
    public void testStream6() {
        baseTest(100 * 1024, 10 * 1024, 10 * 1024, 8 * 1024, true);
    }

    @Test
    public void testStream7() {
        baseTest(100 * 1024, 10 * 1024, 10 * 1024 + 1, 8 * 1024, false);
    }

    @Test
    public void testStream8() {
        baseTest(100 * 1024, 10 * 1024, 8 * 1024, 120 * 1024, false);
    }

    @Test
    public void testStream9() {
        baseTest(100 * 1024, 80 * 1024, 79 * 1024, 1234, true);
    }

    private void baseTest(int bufferSize, int max, int soap, int readBufferSize, boolean success) {

        byte[] boundaryBytes = ("\r\n--" + BOUNDARY + "\r\n").getBytes();

        byte[] byteBuffer = new byte[bufferSize + (boundaryBytes.length) * 3 + 2];

        System.arraycopy(boundaryBytes, 0, byteBuffer, 0, boundaryBytes.length);

        for (int i = 0; i < bufferSize; i++) {

            if (i == soap) {
                System.arraycopy(boundaryBytes, 0, byteBuffer, i + boundaryBytes.length, boundaryBytes.length);
            }
            if (i < soap) {
                byteBuffer[i + boundaryBytes.length] = (byte) 'S';
            } else {
                byteBuffer[i + boundaryBytes.length * 2] = (byte) 'C';
            }

        }
        System.arraycopy(boundaryBytes, 0, byteBuffer, bufferSize + boundaryBytes.length * 2, boundaryBytes.length);
        byteBuffer[byteBuffer.length - 4] = '-';
        byteBuffer[byteBuffer.length - 3] = '-';
        byteBuffer[byteBuffer.length - 2] = '\r';
        byteBuffer[byteBuffer.length - 1] = '\n';

        // test read with buffer
        ByteArrayInputStream originStream = new ByteArrayInputStream(byteBuffer);

        try {
            ProtectionRequestWrapper.CheckServletInputStream stream = new ProtectionRequestWrapper.CheckServletInputStream(
                    originStream, BOUNDARY.getBytes(), max);

            int countS = 0;
            int countC = 0;

            byte[] buffer = new byte[readBufferSize];
            int b;

            assertEquals(0, stream.read(byteBuffer, 0, 0));

            while ((b = stream.read(buffer)) > -1) {
                for (int i = 0; i < b; i++) {
                    if (buffer[i] == 'S') {
                        countS++;
                    }
                    if (buffer[i] == 'C') {
                        countC++;
                    }
                }
            }

            stream.close();

            assertEquals(soap, countS);
            assertEquals(bufferSize - soap, countC);
        } catch (Exception e) {
            if (success) {
                fail();
            }
        }

        // test single byte read
        originStream = new ByteArrayInputStream(byteBuffer);

        try {
            ProtectionRequestWrapper.CheckServletInputStream stream = new ProtectionRequestWrapper.CheckServletInputStream(
                    originStream, BOUNDARY.getBytes(), max);

            int countS = 0;
            int countC = 0;

            int b;
            while ((b = stream.read()) > -1) {
                if (b == 'S') {
                    countS++;
                }
                if (b == 'C') {
                    countC++;
                }
            }

            stream.close();

            assertEquals(soap, countS);
            assertEquals(bufferSize - soap, countC);
        } catch (Exception e) {
            if (success) {
                fail();
            }
        }
    }
}