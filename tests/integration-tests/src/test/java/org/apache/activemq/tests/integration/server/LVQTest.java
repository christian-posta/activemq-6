/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.tests.integration.server;
import org.apache.activemq.api.core.ActiveMQException;
import org.apache.activemq.core.server.Queue;
import org.junit.Before;
import org.junit.After;

import org.junit.Test;

import org.junit.Assert;

import org.apache.activemq.api.core.Message;
import org.apache.activemq.api.core.SimpleString;
import org.apache.activemq.api.core.TransportConfiguration;
import org.apache.activemq.api.core.client.ClientConsumer;
import org.apache.activemq.api.core.client.ClientMessage;
import org.apache.activemq.api.core.client.ClientProducer;
import org.apache.activemq.api.core.client.ClientSession;
import org.apache.activemq.api.core.client.ClientSessionFactory;
import org.apache.activemq.api.core.client.ActiveMQClient;
import org.apache.activemq.api.core.client.ServerLocator;
import org.apache.activemq.core.config.impl.ConfigurationImpl;
import org.apache.activemq.core.server.ActiveMQServer;
import org.apache.activemq.core.server.ActiveMQServers;
import org.apache.activemq.core.settings.impl.AddressSettings;
import org.apache.activemq.tests.util.UnitTestCase;

public class LVQTest extends UnitTestCase
{
   private ActiveMQServer server;

   private ClientSession clientSession;

   private ClientSession clientSessionTxReceives;

   private ClientSession clientSessionTxSends;

   private final SimpleString address = new SimpleString("LVQTestAddress");

   private final SimpleString qName1 = new SimpleString("LVQTestQ1");

   @Test
   public void testSimple() throws Exception
   {
      ClientProducer producer = clientSession.createProducer(address);
      ClientConsumer consumer = clientSession.createConsumer(qName1);
      ClientMessage m1 = createTextMessage(clientSession, "m1");
      SimpleString rh = new SimpleString("SMID1");
      m1.putStringProperty(Message.HDR_LAST_VALUE_NAME, rh);
      ClientMessage m2 = createTextMessage(clientSession, "m2");
      m2.putStringProperty(Message.HDR_LAST_VALUE_NAME, rh);
      producer.send(m1);
      producer.send(m2);
      clientSession.start();
      ClientMessage m = consumer.receive(1000);
      Assert.assertNotNull(m);
      m.acknowledge();
      Assert.assertEquals(m.getBodyBuffer().readString(), "m2");
   }

   @Test
   public void testMultipleMessages() throws Exception
   {
      ClientProducer producer = clientSession.createProducer(address);
      ClientConsumer consumer = clientSession.createConsumer(qName1);
      SimpleString messageId1 = new SimpleString("SMID1");
      SimpleString messageId2 = new SimpleString("SMID2");
      ClientMessage m1 = createTextMessage(clientSession, "m1");
      m1.putStringProperty(Message.HDR_LAST_VALUE_NAME, messageId1);
      ClientMessage m2 = createTextMessage(clientSession, "m2");
      m2.putStringProperty(Message.HDR_LAST_VALUE_NAME, messageId2);
      ClientMessage m3 = createTextMessage(clientSession, "m3");
      m3.putStringProperty(Message.HDR_LAST_VALUE_NAME, messageId1);
      ClientMessage m4 = createTextMessage(clientSession, "m4");
      m4.putStringProperty(Message.HDR_LAST_VALUE_NAME, messageId2);
      producer.send(m1);
      producer.send(m2);
      producer.send(m3);
      producer.send(m4);
      clientSession.start();
      ClientMessage m = consumer.receive(1000);
      Assert.assertNotNull(m);
      m.acknowledge();
      Assert.assertEquals(m.getBodyBuffer().readString(), "m3");
      m = consumer.receive(1000);
      Assert.assertNotNull(m);
      m.acknowledge();
      Assert.assertEquals(m.getBodyBuffer().readString(), "m4");
   }

   @Test
   public void testFirstMessageReceivedButAckedAfter() throws Exception
   {
      ClientProducer producer = clientSession.createProducer(address);
      ClientConsumer consumer = clientSession.createConsumer(qName1);
      ClientMessage m1 = createTextMessage(clientSession, "m1");
      SimpleString rh = new SimpleString("SMID1");
      m1.putStringProperty(Message.HDR_LAST_VALUE_NAME, rh);
      ClientMessage m2 = createTextMessage(clientSession, "m2");
      m2.putStringProperty(Message.HDR_LAST_VALUE_NAME, rh);
      producer.send(m1);
      clientSession.start();
      ClientMessage m = consumer.receive(1000);
      Assert.assertNotNull(m);
      producer.send(m2);
      m.acknowledge();
      Assert.assertEquals(m.getBodyBuffer().readString(), "m1");
      m = consumer.receive(1000);
      Assert.assertNotNull(m);
      m.acknowledge();
      Assert.assertEquals(m.getBodyBuffer().readString(), "m2");
   }

   @Test
   public void testFirstMessageReceivedAndCancelled() throws Exception
   {
      ClientProducer producer = clientSession.createProducer(address);
      ClientConsumer consumer = clientSession.createConsumer(qName1);
      ClientMessage m1 = createTextMessage(clientSession, "m1");
      SimpleString rh = new SimpleString("SMID1");
      m1.putStringProperty(Message.HDR_LAST_VALUE_NAME, rh);
      ClientMessage m2 = createTextMessage(clientSession, "m2");
      m2.putStringProperty(Message.HDR_LAST_VALUE_NAME, rh);
      producer.send(m1);
      clientSession.start();
      ClientMessage m = consumer.receive(1000);
      Assert.assertNotNull(m);
      Assert.assertEquals(m.getBodyBuffer().readString(), "m1");
      producer.send(m2);
      consumer.close();
      consumer = clientSession.createConsumer(qName1);
      m = consumer.receive(1000);
      Assert.assertNotNull(m);
      Assert.assertEquals("m2", m.getBodyBuffer().readString());
      m.acknowledge();
      m = consumer.receiveImmediate();
      Assert.assertNull(m);
   }

   @Test
   public void testManyMessagesReceivedAndCancelled() throws Exception
   {
      ClientProducer producer = clientSession.createProducer(address);
      ClientConsumer consumer = clientSession.createConsumer(qName1);

      SimpleString rh = new SimpleString("SMID1");
      ClientMessage m1 = createTextMessage(clientSession, "m1");
      m1.putStringProperty(Message.HDR_LAST_VALUE_NAME, rh);
      ClientMessage m2 = createTextMessage(clientSession, "m2");
      m2.putStringProperty(Message.HDR_LAST_VALUE_NAME, rh);
      ClientMessage m3 = createTextMessage(clientSession, "m3");
      m3.putStringProperty(Message.HDR_LAST_VALUE_NAME, rh);
      ClientMessage m4 = createTextMessage(clientSession, "m4");
      m4.putStringProperty(Message.HDR_LAST_VALUE_NAME, rh);
      ClientMessage m5 = createTextMessage(clientSession, "m5");
      m5.putStringProperty(Message.HDR_LAST_VALUE_NAME, rh);
      ClientMessage m6 = createTextMessage(clientSession, "m6");
      m6.putStringProperty(Message.HDR_LAST_VALUE_NAME, rh);
      clientSession.start();
      producer.send(m1);
      ClientMessage m = consumer.receive(1000);
      Assert.assertNotNull(m);
      Assert.assertEquals(m.getBodyBuffer().readString(), "m1");
      producer.send(m2);
      m = consumer.receive(1000);
      Assert.assertNotNull(m);
      Assert.assertEquals(m.getBodyBuffer().readString(), "m2");
      producer.send(m3);
      m = consumer.receive(1000);
      Assert.assertNotNull(m);
      Assert.assertEquals(m.getBodyBuffer().readString(), "m3");
      producer.send(m4);
      m = consumer.receive(1000);
      Assert.assertNotNull(m);
      Assert.assertEquals(m.getBodyBuffer().readString(), "m4");
      producer.send(m5);
      m = consumer.receive(1000);
      Assert.assertNotNull(m);
      Assert.assertEquals(m.getBodyBuffer().readString(), "m5");
      producer.send(m6);
      m = consumer.receive(1000);
      Assert.assertNotNull(m);
      Assert.assertEquals(m.getBodyBuffer().readString(), "m6");
      consumer.close();
      consumer = clientSession.createConsumer(qName1);
      m = consumer.receive(1000);
      Assert.assertNotNull(m);
      m.acknowledge();
      Assert.assertEquals(m.getBodyBuffer().readString(), "m6");
      m = consumer.receiveImmediate();
      Assert.assertNull(m);
   }

   @Test
   public void testSimpleInTx() throws Exception
   {

      ClientProducer producer = clientSessionTxReceives.createProducer(address);
      ClientConsumer consumer = clientSessionTxReceives.createConsumer(qName1);
      ClientMessage m1 = createTextMessage(clientSession, "m1");
      SimpleString rh = new SimpleString("SMID1");
      m1.putStringProperty(Message.HDR_LAST_VALUE_NAME, rh);
      ClientMessage m2 = createTextMessage(clientSession, "m2");
      m2.putStringProperty(Message.HDR_LAST_VALUE_NAME, rh);
      producer.send(m1);
      producer.send(m2);
      clientSessionTxReceives.start();
      ClientMessage m = consumer.receive(1000);
      Assert.assertNotNull(m);
      m.acknowledge();
      Assert.assertEquals(m.getBodyBuffer().readString(), "m2");
   }

   @Test
   public void testMultipleMessagesInTx() throws Exception
   {
      ClientProducer producer = clientSessionTxReceives.createProducer(address);
      ClientConsumer consumer = clientSessionTxReceives.createConsumer(qName1);
      SimpleString messageId1 = new SimpleString("SMID1");
      SimpleString messageId2 = new SimpleString("SMID2");
      ClientMessage m1 = createTextMessage(clientSession, "m1");
      m1.putStringProperty(Message.HDR_LAST_VALUE_NAME, messageId1);
      ClientMessage m2 = createTextMessage(clientSession, "m2");
      m2.putStringProperty(Message.HDR_LAST_VALUE_NAME, messageId2);
      ClientMessage m3 = createTextMessage(clientSession, "m3");
      m3.putStringProperty(Message.HDR_LAST_VALUE_NAME, messageId1);
      ClientMessage m4 = createTextMessage(clientSession, "m4");
      m4.putStringProperty(Message.HDR_LAST_VALUE_NAME, messageId2);
      producer.send(m1);
      producer.send(m2);
      producer.send(m3);
      producer.send(m4);
      clientSessionTxReceives.start();
      ClientMessage m = consumer.receive(1000);
      Assert.assertNotNull(m);
      m.acknowledge();
      Assert.assertEquals(m.getBodyBuffer().readString(), "m3");
      m = consumer.receive(1000);
      Assert.assertNotNull(m);
      m.acknowledge();
      Assert.assertEquals(m.getBodyBuffer().readString(), "m4");
      clientSessionTxReceives.commit();
      m = consumer.receiveImmediate();
      Assert.assertNull(m);
   }

   @Test
   public void testMultipleMessagesInTxRollback() throws Exception
   {
      ClientProducer producer = clientSessionTxReceives.createProducer(address);
      ClientConsumer consumer = clientSessionTxReceives.createConsumer(qName1);
      SimpleString messageId1 = new SimpleString("SMID1");
      SimpleString messageId2 = new SimpleString("SMID2");
      ClientMessage m1 = createTextMessage(clientSession, "m1");
      m1.putStringProperty(Message.HDR_LAST_VALUE_NAME, messageId1);
      ClientMessage m2 = createTextMessage(clientSession, "m2");
      m2.putStringProperty(Message.HDR_LAST_VALUE_NAME, messageId2);
      ClientMessage m3 = createTextMessage(clientSession, "m3");
      m3.putStringProperty(Message.HDR_LAST_VALUE_NAME, messageId1);
      ClientMessage m4 = createTextMessage(clientSession, "m4");
      m4.putStringProperty(Message.HDR_LAST_VALUE_NAME, messageId2);
      producer.send(m1);
      producer.send(m2);
      clientSessionTxReceives.start();
      ClientMessage m = consumer.receive(1000);
      Assert.assertNotNull(m);
      m.acknowledge();
      Assert.assertEquals(m.getBodyBuffer().readString(), "m1");
      m = consumer.receive(1000);
      Assert.assertNotNull(m);
      m.acknowledge();
      Assert.assertEquals(m.getBodyBuffer().readString(), "m2");
      producer.send(m3);
      producer.send(m4);
      m = consumer.receive(1000);
      Assert.assertNotNull(m);
      m.acknowledge();
      Assert.assertEquals(m.getBodyBuffer().readString(), "m3");
      m = consumer.receive(1000);
      Assert.assertNotNull(m);
      m.acknowledge();
      Assert.assertEquals(m.getBodyBuffer().readString(), "m4");
      clientSessionTxReceives.rollback();
      m = consumer.receive(1000);
      Assert.assertNotNull(m);
      m.acknowledge();
      Assert.assertEquals(m.getBodyBuffer().readString(), "m3");
      m = consumer.receive(1000);
      Assert.assertNotNull(m);
      m.acknowledge();
      Assert.assertEquals(m.getBodyBuffer().readString(), "m4");
   }

   @Test
   public void testSingleTXRollback() throws Exception
   {
      ClientProducer producer = clientSessionTxReceives.createProducer(address);
      ClientConsumer consumer = clientSessionTxReceives.createConsumer(qName1);
      SimpleString messageId1 = new SimpleString("SMID1");
      ClientMessage m1 = createTextMessage(clientSession, "m1");
      m1.putStringProperty(Message.HDR_LAST_VALUE_NAME, messageId1);
      producer.send(m1);
      clientSessionTxReceives.start();
      ClientMessage m = consumer.receive(1000);
      Assert.assertNotNull(m);
      m.acknowledge();
      clientSessionTxReceives.rollback();
      m = consumer.receive(1000);
      Assert.assertNotNull(m);
      m.acknowledge();
      Assert.assertEquals(m.getBodyBuffer().readString(), "m1");
      Assert.assertNull(consumer.receiveImmediate());
   }

   @Test
   public void testMultipleMessagesInTxSend() throws Exception
   {
      ClientProducer producer = clientSessionTxSends.createProducer(address);
      ClientConsumer consumer = clientSessionTxSends.createConsumer(qName1);
      SimpleString rh = new SimpleString("SMID1");
      ClientMessage m1 = createTextMessage(clientSession, "m1");
      m1.putStringProperty(Message.HDR_LAST_VALUE_NAME, rh);
      ClientMessage m2 = createTextMessage(clientSession, "m2");
      m2.putStringProperty(Message.HDR_LAST_VALUE_NAME, rh);
      ClientMessage m3 = createTextMessage(clientSession, "m3");
      m3.putStringProperty(Message.HDR_LAST_VALUE_NAME, rh);
      ClientMessage m4 = createTextMessage(clientSession, "m4");
      m4.putStringProperty(Message.HDR_LAST_VALUE_NAME, rh);
      ClientMessage m5 = createTextMessage(clientSession, "m5");
      m5.putStringProperty(Message.HDR_LAST_VALUE_NAME, rh);
      ClientMessage m6 = createTextMessage(clientSession, "m6");
      m6.putStringProperty(Message.HDR_LAST_VALUE_NAME, rh);
      producer.send(m1);
      producer.send(m2);
      producer.send(m3);
      producer.send(m4);
      producer.send(m5);
      producer.send(m6);
      clientSessionTxSends.commit();
      clientSessionTxSends.start();
      ClientMessage m = consumer.receive(1000);
      Assert.assertNotNull(m);
      m.acknowledge();
      Assert.assertEquals(m.getBodyBuffer().readString(), "m6");
   }

   @Test
   public void testMultipleMessagesPersistedCorrectly() throws Exception
   {
      ClientProducer producer = clientSession.createProducer(address);
      ClientConsumer consumer = clientSession.createConsumer(qName1);
      SimpleString rh = new SimpleString("SMID1");
      ClientMessage m1 = createTextMessage(clientSession, "m1");
      m1.putStringProperty(Message.HDR_LAST_VALUE_NAME, rh);
      m1.setDurable(true);
      ClientMessage m2 = createTextMessage(clientSession, "m2");
      m2.putStringProperty(Message.HDR_LAST_VALUE_NAME, rh);
      m2.setDurable(true);
      ClientMessage m3 = createTextMessage(clientSession, "m3");
      m3.putStringProperty(Message.HDR_LAST_VALUE_NAME, rh);
      m3.setDurable(true);
      ClientMessage m4 = createTextMessage(clientSession, "m4");
      m4.putStringProperty(Message.HDR_LAST_VALUE_NAME, rh);
      m4.setDurable(true);
      ClientMessage m5 = createTextMessage(clientSession, "m5");
      m5.putStringProperty(Message.HDR_LAST_VALUE_NAME, rh);
      m5.setDurable(true);
      ClientMessage m6 = createTextMessage(clientSession, "m6");
      m6.putStringProperty(Message.HDR_LAST_VALUE_NAME, rh);
      m6.setDurable(true);
      producer.send(m1);
      producer.send(m2);
      producer.send(m3);
      producer.send(m4);
      producer.send(m5);
      producer.send(m6);
      clientSession.start();
      ClientMessage m = consumer.receive(1000);
      Assert.assertNotNull(m);
      m.acknowledge();
      Assert.assertEquals(m.getBodyBuffer().readString(), "m6");
      m = consumer.receiveImmediate();
      Assert.assertNull(m);
   }

   @Test
   public void testMultipleMessagesPersistedCorrectlyInTx() throws Exception
   {
      ClientProducer producer = clientSessionTxSends.createProducer(address);
      ClientConsumer consumer = clientSessionTxSends.createConsumer(qName1);
      SimpleString rh = new SimpleString("SMID1");
      ClientMessage m1 = createTextMessage(clientSession, "m1");
      m1.putStringProperty(Message.HDR_LAST_VALUE_NAME, rh);
      m1.setDurable(true);
      ClientMessage m2 = createTextMessage(clientSession, "m2");
      m2.putStringProperty(Message.HDR_LAST_VALUE_NAME, rh);
      m2.setDurable(true);
      ClientMessage m3 = createTextMessage(clientSession, "m3");
      m3.putStringProperty(Message.HDR_LAST_VALUE_NAME, rh);
      m3.setDurable(true);
      ClientMessage m4 = createTextMessage(clientSession, "m4");
      m4.putStringProperty(Message.HDR_LAST_VALUE_NAME, rh);
      m4.setDurable(true);
      ClientMessage m5 = createTextMessage(clientSession, "m5");
      m5.putStringProperty(Message.HDR_LAST_VALUE_NAME, rh);
      m5.setDurable(true);
      ClientMessage m6 = createTextMessage(clientSession, "m6");
      m6.putStringProperty(Message.HDR_LAST_VALUE_NAME, rh);
      m6.setDurable(true);
      producer.send(m1);
      producer.send(m2);
      producer.send(m3);
      producer.send(m4);
      producer.send(m5);
      producer.send(m6);
      clientSessionTxSends.commit();
      clientSessionTxSends.start();
      ClientMessage m = consumer.receive(1000);
      Assert.assertNotNull(m);
      m.acknowledge();
      Assert.assertEquals(m.getBodyBuffer().readString(), "m6");
      m = consumer.receiveImmediate();
      Assert.assertNull(m);
   }

   @Test
   public void testMultipleAcksPersistedCorrectly() throws Exception
   {

      Queue queue = server.locateQueue(qName1);
      ClientProducer producer = clientSession.createProducer(address);
      ClientConsumer consumer = clientSession.createConsumer(qName1);
      SimpleString rh = new SimpleString("SMID1");
      ClientMessage m1 = createTextMessage(clientSession, "m1");
      m1.putStringProperty(Message.HDR_LAST_VALUE_NAME, rh);
      m1.setDurable(true);
      ClientMessage m2 = createTextMessage(clientSession, "m2");
      m2.putStringProperty(Message.HDR_LAST_VALUE_NAME, rh);
      m2.setDurable(true);
      ClientMessage m3 = createTextMessage(clientSession, "m3");
      m3.putStringProperty(Message.HDR_LAST_VALUE_NAME, rh);
      m3.setDurable(true);
      ClientMessage m4 = createTextMessage(clientSession, "m4");
      m4.putStringProperty(Message.HDR_LAST_VALUE_NAME, rh);
      m4.setDurable(true);
      ClientMessage m5 = createTextMessage(clientSession, "m5");
      m5.putStringProperty(Message.HDR_LAST_VALUE_NAME, rh);
      m5.setDurable(true);
      ClientMessage m6 = createTextMessage(clientSession, "m6");
      m6.putStringProperty(Message.HDR_LAST_VALUE_NAME, rh);
      m6.setDurable(true);
      clientSession.start();
      producer.send(m1);
      ClientMessage m = consumer.receive(1000);
      Assert.assertNotNull(m);
      m.acknowledge();
      Assert.assertEquals(m.getBodyBuffer().readString(), "m1");
      producer.send(m2);
      m = consumer.receive(1000);
      Assert.assertNotNull(m);
      m.acknowledge();
      Assert.assertEquals(m.getBodyBuffer().readString(), "m2");
      producer.send(m3);
      m = consumer.receive(1000);
      Assert.assertNotNull(m);
      m.acknowledge();
      Assert.assertEquals(m.getBodyBuffer().readString(), "m3");
      producer.send(m4);
      m = consumer.receive(1000);
      Assert.assertNotNull(m);
      m.acknowledge();
      Assert.assertEquals(m.getBodyBuffer().readString(), "m4");
      producer.send(m5);
      m = consumer.receive(1000);
      Assert.assertNotNull(m);
      m.acknowledge();
      Assert.assertEquals(m.getBodyBuffer().readString(), "m5");
      producer.send(m6);
      m = consumer.receive(1000);
      Assert.assertNotNull(m);
      m.acknowledge();
      Assert.assertEquals(m.getBodyBuffer().readString(), "m6");

      assertEquals(0, queue.getDeliveringCount());
   }

   @Test
   public void testRemoveMessageThroughManagement() throws Exception
   {

      Queue queue = server.locateQueue(qName1);
      ClientProducer producer = clientSession.createProducer(address);
      ClientConsumer consumer = clientSession.createConsumer(qName1);
      SimpleString rh = new SimpleString("SMID1");
      ClientMessage m1 = createTextMessage(clientSession, "m1");
      m1.putStringProperty(Message.HDR_LAST_VALUE_NAME, rh);
      m1.setDurable(true);
      producer.send(m1);

      queue.deleteAllReferences();

      producer.send(m1);

      clientSession.start();
      ClientMessage m = consumer.receive(1000);
      Assert.assertNotNull(m);
      m.acknowledge();
      Assert.assertEquals(m.getBodyBuffer().readString(), "m1");

      assertEquals(0, queue.getDeliveringCount());
   }

   @Test
   public void testMultipleAcksPersistedCorrectly2() throws Exception
   {

      Queue queue = server.locateQueue(qName1);
      ClientProducer producer = clientSession.createProducer(address);
      ClientConsumer consumer = clientSession.createConsumer(qName1);
      SimpleString rh = new SimpleString("SMID1");
      ClientMessage m1 = createTextMessage(clientSession, "m1");
      m1.putStringProperty(Message.HDR_LAST_VALUE_NAME, rh);
      m1.setDurable(true);
      ClientMessage m2 = createTextMessage(clientSession, "m2");
      m2.putStringProperty(Message.HDR_LAST_VALUE_NAME, rh);
      m2.setDurable(true);
      clientSession.start();
      producer.send(m1);
      ClientMessage m = consumer.receive(1000);
      Assert.assertNotNull(m);
      m.acknowledge();
      Assert.assertEquals(m.getBodyBuffer().readString(), "m1");
      producer.send(m2);
      m = consumer.receive(1000);
      Assert.assertNotNull(m);
      m.acknowledge();
      Assert.assertEquals(m.getBodyBuffer().readString(), "m2");

      assertEquals(0, queue.getDeliveringCount());
   }

   @Test
   public void testMultipleAcksPersistedCorrectlyInTx() throws Exception
   {
      ClientProducer producer = clientSessionTxReceives.createProducer(address);
      ClientConsumer consumer = clientSessionTxReceives.createConsumer(qName1);
      SimpleString rh = new SimpleString("SMID1");
      ClientMessage m1 = createTextMessage(clientSession, "m1");
      m1.putStringProperty(Message.HDR_LAST_VALUE_NAME, rh);
      m1.setDurable(true);
      ClientMessage m2 = createTextMessage(clientSession, "m2");
      m2.putStringProperty(Message.HDR_LAST_VALUE_NAME, rh);
      m2.setDurable(true);
      ClientMessage m3 = createTextMessage(clientSession, "m3");
      m3.putStringProperty(Message.HDR_LAST_VALUE_NAME, rh);
      m3.setDurable(true);
      ClientMessage m4 = createTextMessage(clientSession, "m4");
      m4.putStringProperty(Message.HDR_LAST_VALUE_NAME, rh);
      m4.setDurable(true);
      ClientMessage m5 = createTextMessage(clientSession, "m5");
      m5.putStringProperty(Message.HDR_LAST_VALUE_NAME, rh);
      m5.setDurable(true);
      ClientMessage m6 = createTextMessage(clientSession, "m6");
      m6.putStringProperty(Message.HDR_LAST_VALUE_NAME, rh);
      m6.setDurable(true);
      clientSessionTxReceives.start();
      producer.send(m1);
      ClientMessage m = consumer.receive(1000);
      Assert.assertNotNull(m);
      m.acknowledge();
      Assert.assertEquals(m.getBodyBuffer().readString(), "m1");
      producer.send(m2);
      m = consumer.receive(1000);
      Assert.assertNotNull(m);
      m.acknowledge();
      Assert.assertEquals(m.getBodyBuffer().readString(), "m2");
      producer.send(m3);
      m = consumer.receive(1000);
      Assert.assertNotNull(m);
      m.acknowledge();
      Assert.assertEquals(m.getBodyBuffer().readString(), "m3");
      producer.send(m4);
      m = consumer.receive(1000);
      Assert.assertNotNull(m);
      m.acknowledge();
      Assert.assertEquals(m.getBodyBuffer().readString(), "m4");
      producer.send(m5);
      m = consumer.receive(1000);
      Assert.assertNotNull(m);
      m.acknowledge();
      Assert.assertEquals(m.getBodyBuffer().readString(), "m5");
      producer.send(m6);
      m = consumer.receive(1000);
      Assert.assertNotNull(m);
      m.acknowledge();
      Assert.assertEquals(m.getBodyBuffer().readString(), "m6");
      clientSessionTxReceives.commit();
   }

   @Override
   @After
   public void tearDown() throws Exception
   {
      if (clientSession != null)
      {
         try
         {
            clientSession.close();
         }
         catch (ActiveMQException e1)
         {
            //
         }
      }

      if (clientSessionTxReceives != null)
      {
         try
         {
            clientSessionTxReceives.close();
         }
         catch (ActiveMQException e1)
         {
            //
         }
      }

      if (clientSessionTxSends != null)
      {
         try
         {
            clientSessionTxSends.close();
         }
         catch (ActiveMQException e1)
         {
            //
         }
      }
      if (server != null && server.isStarted())
      {
         try
         {
            server.stop();
         }
         catch (Exception e1)
         {
            //
         }
      }
      server = null;
      clientSession = null;

      super.tearDown();
   }

   @Override
   @Before
   public void setUp() throws Exception
   {
      super.setUp();

      ConfigurationImpl configuration = createBasicConfig()
         .addAcceptorConfiguration(new TransportConfiguration(UnitTestCase.INVM_ACCEPTOR_FACTORY));
      server = ActiveMQServers.newActiveMQServer(configuration, false);
      // start the server
      server.start();

      AddressSettings qs = new AddressSettings();
      qs.setLastValueQueue(true);
      server.getAddressSettingsRepository().addMatch(address.toString(), qs);
      // then we create a client as normalServer
      ServerLocator locator = ActiveMQClient.createServerLocatorWithoutHA(new TransportConfiguration(UnitTestCase.INVM_CONNECTOR_FACTORY));
      locator.setBlockOnAcknowledge(true);
      locator.setAckBatchSize(0);
      ClientSessionFactory sessionFactory = createSessionFactory(locator);
      clientSession = sessionFactory.createSession(false, true, true);
      clientSessionTxReceives = sessionFactory.createSession(false, true, false);
      clientSessionTxSends = sessionFactory.createSession(false, false, true);
      clientSession.createQueue(address, qName1, null, true);
   }
}
