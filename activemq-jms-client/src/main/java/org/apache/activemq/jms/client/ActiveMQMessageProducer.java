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
package org.apache.activemq.jms.client;

import javax.jms.BytesMessage;
import javax.jms.CompletionListener;
import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.IllegalStateException;
import javax.jms.InvalidDestinationException;
import javax.jms.JMSException;
import javax.jms.MapMessage;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.ObjectMessage;
import javax.jms.Queue;
import javax.jms.QueueSender;
import javax.jms.StreamMessage;
import javax.jms.TextMessage;
import javax.jms.Topic;
import javax.jms.TopicPublisher;

import org.apache.activemq.api.core.ActiveMQException;
import org.apache.activemq.api.core.SimpleString;
import org.apache.activemq.api.core.client.ClientMessage;
import org.apache.activemq.api.core.client.ClientProducer;
import org.apache.activemq.api.core.client.ClientSession;
import org.apache.activemq.api.core.client.SendAcknowledgementHandler;
import org.apache.activemq.utils.UUID;
import org.apache.activemq.utils.UUIDGenerator;
/**
 * ActiveMQ implementation of a JMS MessageProducer.
 */
public class ActiveMQMessageProducer implements MessageProducer, QueueSender, TopicPublisher
{
   private final ActiveMQConnection connection;

   private final SimpleString connID;

   private final ClientProducer clientProducer;
   private final ClientSession clientSession;

   private boolean disableMessageID = false;

   private boolean disableMessageTimestamp = false;

   private int defaultPriority = Message.DEFAULT_PRIORITY;
   private long defaultTimeToLive = Message.DEFAULT_TIME_TO_LIVE;
   private int defaultDeliveryMode = Message.DEFAULT_DELIVERY_MODE;
   private long defaultDeliveryDelay = Message.DEFAULT_DELIVERY_DELAY;

   private final ActiveMQDestination defaultDestination;
   // Constructors --------------------------------------------------

   protected ActiveMQMessageProducer(final ActiveMQConnection connection, final ClientProducer producer,
                                     final ActiveMQDestination defaultDestination, final ClientSession clientSession) throws JMSException
   {
      this.connection = connection;

      connID = connection.getClientID() != null ? new SimpleString(connection.getClientID()) : connection.getUID();

      this.clientProducer = producer;

      this.defaultDestination = defaultDestination;

      this.clientSession = clientSession;
   }

   // MessageProducer implementation --------------------------------

   public void setDisableMessageID(final boolean value) throws JMSException
   {
      checkClosed();

      disableMessageID = value;
   }

   public boolean getDisableMessageID() throws JMSException
   {
      checkClosed();

      return disableMessageID;
   }

   public void setDisableMessageTimestamp(final boolean value) throws JMSException
   {
      checkClosed();

      disableMessageTimestamp = value;
   }

   public boolean getDisableMessageTimestamp() throws JMSException
   {
      checkClosed();

      return disableMessageTimestamp;
   }

   public void setDeliveryMode(final int deliveryMode) throws JMSException
   {
      checkClosed();
      if (deliveryMode != DeliveryMode.NON_PERSISTENT && deliveryMode != DeliveryMode.PERSISTENT)
      {
         throw ActiveMQJMSClientBundle.BUNDLE.illegalDeliveryMode(deliveryMode);
      }

      defaultDeliveryMode = deliveryMode;
   }

   public int getDeliveryMode() throws JMSException
   {
      checkClosed();

      return defaultDeliveryMode;
   }

   public void setPriority(final int defaultPriority) throws JMSException
   {
      checkClosed();

      if (defaultPriority < 0 || defaultPriority > 9)
      {
         throw new JMSException("Illegal priority value: " + defaultPriority);
      }

      this.defaultPriority = defaultPriority;
   }

   public int getPriority() throws JMSException
   {
      checkClosed();

      return defaultPriority;
   }

   public void setTimeToLive(final long timeToLive) throws JMSException
   {
      checkClosed();

      defaultTimeToLive = timeToLive;
   }

   public long getTimeToLive() throws JMSException
   {
      checkClosed();

      return defaultTimeToLive;
   }

   public Destination getDestination() throws JMSException
   {
      checkClosed();

      return defaultDestination;
   }

   public void close() throws JMSException
   {
      connection.getThreadAwareContext().assertNotCompletionListenerThread();
      try
      {
         clientProducer.close();
      }
      catch (ActiveMQException e)
      {
         throw JMSExceptionHelper.convertFromActiveMQException(e);
      }
   }

   public void send(final Message message) throws JMSException
   {
      checkDefaultDestination();
      doSendx(defaultDestination, message, defaultDeliveryMode, defaultPriority, defaultTimeToLive, null);
   }

   public void send(final Message message,
                    final int deliveryMode,
                    final int priority, final long timeToLive) throws JMSException
   {
      checkDefaultDestination();
      doSendx(defaultDestination, message, deliveryMode, priority, timeToLive, null);
   }

   public void send(final Destination destination, final Message message) throws JMSException
   {
      send(destination, message, defaultDeliveryMode, defaultPriority, defaultTimeToLive);
   }

   public void send(final Destination destination, final Message message, final int deliveryMode, final int priority,
                    final long timeToLive) throws JMSException
   {
      checkClosed();

      checkDestination(destination);

      doSendx((ActiveMQDestination)destination, message, deliveryMode, priority, timeToLive, null);
   }

   @Override
   public void setDeliveryDelay(long deliveryDelay) throws JMSException
   {
      this.defaultDeliveryDelay = deliveryDelay;
   }

   @Override
   public long getDeliveryDelay() throws JMSException
   {
      return defaultDeliveryDelay;
   }

   @Override
   public void send(Message message, CompletionListener completionListener) throws JMSException
   {
      send(message, defaultDeliveryMode, defaultPriority, defaultTimeToLive, completionListener);
   }

   @Override
   public void send(Message message, int deliveryMode, int priority, long timeToLive,
                    CompletionListener completionListener) throws JMSException
   {
      checkCompletionListener(completionListener);
      checkDefaultDestination();
      doSendx(defaultDestination, message, deliveryMode, priority, timeToLive, completionListener);
   }

   @Override
   public void send(Destination destination, Message message, CompletionListener completionListener) throws JMSException
   {
      send(destination, message, defaultDeliveryMode, defaultPriority, defaultTimeToLive, completionListener);
   }

   @Override
   public void send(Destination destination, Message message, int deliveryMode, int priority, long timeToLive,
                    CompletionListener completionListener) throws JMSException
   {
      checkClosed();

      checkCompletionListener(completionListener);

      checkDestination(destination);

      doSendx((ActiveMQDestination)destination, message, deliveryMode, priority, timeToLive, completionListener);
   }

   // TopicPublisher Implementation ---------------------------------

   public Topic getTopic() throws JMSException
   {
      return (Topic)getDestination();
   }

   public void publish(final Message message) throws JMSException
   {
      send(message);
   }

   public void publish(final Topic topic, final Message message) throws JMSException
   {
      send(topic, message);
   }

   public void publish(final Message message, final int deliveryMode, final int priority, final long timeToLive) throws JMSException
   {
      send(message, deliveryMode, priority, timeToLive);
   }

   public void publish(final Topic topic, final Message message, final int deliveryMode, final int priority,
                       final long timeToLive) throws JMSException
   {
      checkDestination(topic);
      doSendx((ActiveMQDestination)topic, message, deliveryMode, priority, timeToLive, null);
   }

   // QueueSender Implementation ------------------------------------

   public void send(final Queue queue, final Message message) throws JMSException
   {
      send((Destination)queue, message);
   }

   public void send(final Queue queue, final Message message, final int deliveryMode, final int priority,
                    final long timeToLive) throws JMSException
   {
      checkDestination(queue);
      doSendx((ActiveMQDestination)queue, message, deliveryMode, priority, timeToLive, null);
   }

   public Queue getQueue() throws JMSException
   {
      return (Queue)getDestination();
   }

   // Public --------------------------------------------------------

   @Override
   public String toString()
   {
      return "ActiveMQMessageProducer->" + clientProducer;
   }

   /**
    * Check if the default destination has been set
    */
   private void checkDefaultDestination()
   {
      if (defaultDestination == null)
      {
         throw new UnsupportedOperationException("Cannot specify destination if producer has a default destination");
      }
   }

   /**
    * Check if the destination is sent correctly
    */
   private void checkDestination(Destination destination) throws InvalidDestinationException
   {
      if (destination != null && !(destination instanceof ActiveMQDestination))
      {
         throw new InvalidDestinationException("Not a ActiveMQ Destination:" + destination);
      }
      if (destination != null && defaultDestination != null)
      {
         throw new UnsupportedOperationException("Cannot specify destination if producer has a default destination");
      }
      if (destination == null)
      {
         throw ActiveMQJMSClientBundle.BUNDLE.nullTopic();
      }
   }

   private void checkCompletionListener(CompletionListener completionListener)
   {
      if (completionListener == null)
      {
         throw ActiveMQJMSClientBundle.BUNDLE.nullArgumentNotAllowed("CompletionListener");
      }
   }


   private void doSendx(ActiveMQDestination destination, final Message jmsMessage, final int deliveryMode,
                        final int priority, final long timeToLive,
                        CompletionListener completionListener) throws JMSException
   {

      jmsMessage.setJMSDeliveryMode(deliveryMode);

      jmsMessage.setJMSPriority(priority);


      if (timeToLive == 0)
      {
         jmsMessage.setJMSExpiration(0);
      }
      else
      {
         jmsMessage.setJMSExpiration(System.currentTimeMillis() + timeToLive);
      }

      if (!disableMessageTimestamp)
      {
         jmsMessage.setJMSTimestamp(System.currentTimeMillis());
      }
      else
      {
         jmsMessage.setJMSTimestamp(0);
      }

      SimpleString address = null;

      if (destination == null)
      {
         if (defaultDestination == null)
         {
            throw new UnsupportedOperationException("Destination must be specified on send with an anonymous producer");
         }

         destination = defaultDestination;
      }
      else
      {
         if (defaultDestination != null)
         {
            if (!destination.equals(defaultDestination))
            {
               throw new UnsupportedOperationException("Where a default destination is specified " + "for the sender and a destination is "
                                                          + "specified in the arguments to the send, "
                                                          + "these destinations must be equal");
            }
         }

         address = destination.getSimpleAddress();

         if (!connection.containsKnownDestination(address))
         {
            try
            {
               ClientSession.AddressQuery query = clientSession.addressQuery(address);

               // if it's autoCreateJMSQueue we will let the PostOffice.route to execute the creation at the server's side
               // as that's a more efficient path for such operation
               if (!query.isExists() && !query.isAutoCreateJmsQueues())
               {
                  throw new InvalidDestinationException("Destination " + address + " does not exist");
               }
               else
               {
                  connection.addKnownDestination(address);
               }
            }
            catch (ActiveMQException e)
            {
               throw JMSExceptionHelper.convertFromActiveMQException(e);
            }
         }
      }

      ActiveMQMessage activeMQJmsMessage;

      boolean foreign = false;

      // First convert from foreign message if appropriate
      if (!(jmsMessage instanceof ActiveMQMessage))
      {
         // JMS 1.1 Sect. 3.11.4: A provider must be prepared to accept, from a client,
         // a message whose implementation is not one of its own.

         if (jmsMessage instanceof BytesMessage)
         {
            activeMQJmsMessage = new ActiveMQBytesMessage((BytesMessage)jmsMessage, clientSession);
         }
         else if (jmsMessage instanceof MapMessage)
         {
            activeMQJmsMessage = new ActiveMQMapMessage((MapMessage)jmsMessage, clientSession);
         }
         else if (jmsMessage instanceof ObjectMessage)
         {
            activeMQJmsMessage = new ActiveMQObjectMessage((ObjectMessage)jmsMessage, clientSession);
         }
         else if (jmsMessage instanceof StreamMessage)
         {
            activeMQJmsMessage = new ActiveMQStreamMessage((StreamMessage)jmsMessage, clientSession);
         }
         else if (jmsMessage instanceof TextMessage)
         {
            activeMQJmsMessage = new ActiveMQTextMessage((TextMessage)jmsMessage, clientSession);
         }
         else
         {
            activeMQJmsMessage = new ActiveMQMessage(jmsMessage, clientSession);
         }

         // Set the destination on the original message
         jmsMessage.setJMSDestination(destination);

         foreign = true;
      }
      else
      {
         activeMQJmsMessage = (ActiveMQMessage)jmsMessage;
      }

      if (!disableMessageID)
      {
         // Generate a JMS id

         UUID uid = UUIDGenerator.getInstance().generateUUID();

         activeMQJmsMessage.getCoreMessage().setUserID(uid);

         activeMQJmsMessage.resetMessageID(null);
      }

      if (foreign)
      {
         jmsMessage.setJMSMessageID(activeMQJmsMessage.getJMSMessageID());
      }

      activeMQJmsMessage.setJMSDestination(destination);

      try
      {
         activeMQJmsMessage.doBeforeSend();
      }
      catch (Exception e)
      {
         JMSException je = new JMSException(e.getMessage());

         je.initCause(e);

         throw je;
      }

      if (defaultDeliveryDelay > 0)
      {
         activeMQJmsMessage.setJMSDeliveryTime(System.currentTimeMillis() + defaultDeliveryDelay);
      }

      ClientMessage coreMessage = activeMQJmsMessage.getCoreMessage();
      coreMessage.putStringProperty(ActiveMQConnection.CONNECTION_ID_PROPERTY_NAME, connID);

      try
      {
         /**
          * Using a completionListener requires wrapping using a {@link CompletionListenerWrapper},
          * so we avoid it if we can.
          */
         if (completionListener != null)
         {
            clientProducer.send(address, coreMessage, new CompletionListenerWrapper(completionListener, jmsMessage, this));
         }
         else
         {
            clientProducer.send(address, coreMessage);
         }
      }
      catch (ActiveMQException e)
      {
         throw JMSExceptionHelper.convertFromActiveMQException(e);
      }
   }

   private void checkClosed() throws JMSException
   {
      if (clientProducer.isClosed() || clientSession.isClosed())
      {
         throw new IllegalStateException("Producer is closed");
      }
   }

   private static final class CompletionListenerWrapper implements SendAcknowledgementHandler
   {
      private final CompletionListener completionListener;
      private final Message jmsMessage;
      private final ActiveMQMessageProducer producer;

      /**
       * @param jmsMessage
       * @param producer
       */
      public CompletionListenerWrapper(CompletionListener listener, Message jmsMessage, ActiveMQMessageProducer producer)
      {
         this.completionListener = listener;
         this.jmsMessage = jmsMessage;
         this.producer = producer;
      }

      @Override
      public void sendAcknowledged(org.apache.activemq.api.core.Message clientMessage)
      {
         if (jmsMessage instanceof StreamMessage)
         {
            try
            {
               ((StreamMessage)jmsMessage).reset();
            }
            catch (JMSException e)
            {
               // HORNETQ-1209 XXX ignore?
            }
         }
         if (jmsMessage instanceof BytesMessage)
         {
            try
            {
               ((BytesMessage)jmsMessage).reset();
            }
            catch (JMSException e)
            {
               // HORNETQ-1209 XXX ignore?
            }
         }

         try
         {
            producer.connection.getThreadAwareContext().setCurrentThread(true);
            completionListener.onCompletion(jmsMessage);
         }
         finally
         {
            producer.connection.getThreadAwareContext().clearCurrentThread(true);
         }
      }

      @Override
      public String toString()
      {
         return CompletionListenerWrapper.class.getSimpleName() + "( completionListener=" + completionListener + ")";
      }
   }
}
