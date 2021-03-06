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
package org.apache.activemq.uri;

import org.apache.activemq.api.core.DiscoveryGroupConfiguration;
import org.apache.activemq.api.core.UDPBroadcastEndpointFactory;
import org.apache.activemq.api.core.client.ActiveMQClient;
import org.apache.activemq.api.core.client.ServerLocator;
import org.apache.activemq.utils.uri.SchemaConstants;
import org.apache.activemq.utils.uri.URISchema;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class UDPServerLocatorSchema extends AbstractServerLocatorSchema
{
   protected static List<String> IGNORED = new ArrayList<>();
   static
   {
      IGNORED.add("localBindAddress");
      IGNORED.add("localBindPort");
   }
   @Override
   public String getSchemaName()
   {
      return SchemaConstants.UDP;
   }

   @Override
   protected ServerLocator internalNewObject(URI uri, Map<String, String> query, String name) throws Exception
   {
      ConnectionOptions options = newConnectionOptions(uri, query);

      DiscoveryGroupConfiguration dgc = getDiscoveryGroupConfiguration(uri, query, getHost(uri), getPort(uri), name);

      if (options.isHa())
      {
         return ActiveMQClient.createServerLocatorWithHA(dgc);
      }
      else
      {
         return ActiveMQClient.createServerLocatorWithoutHA(dgc);
      }
   }

   @Override
   protected URI internalNewURI(ServerLocator bean) throws Exception
   {
      DiscoveryGroupConfiguration dgc = bean.getDiscoveryGroupConfiguration();
      UDPBroadcastEndpointFactory endpoint = (UDPBroadcastEndpointFactory) dgc.getBroadcastEndpointFactory();
      dgc.setBroadcastEndpointFactory(endpoint);
      String query = URISchema.getData(IGNORED, bean, dgc, endpoint);
      return new URI(SchemaConstants.UDP, null,  endpoint.getGroupAddress(), endpoint.getGroupPort(), null, query, null);
   }

   public static DiscoveryGroupConfiguration getDiscoveryGroupConfiguration(URI uri, Map<String, String> query, String host, int port, String name) throws Exception
   {
      UDPBroadcastEndpointFactory endpointFactoryConfiguration = new UDPBroadcastEndpointFactory()
               .setGroupAddress(host)
               .setGroupPort(port);

      URISchema.setData(uri, endpointFactoryConfiguration, query);

      DiscoveryGroupConfiguration dgc = URISchema.setData(uri, new DiscoveryGroupConfiguration(), query)
            .setName(name)
         .setBroadcastEndpointFactory(endpointFactoryConfiguration);

      URISchema.setData(uri, dgc, query);
      return dgc;
   }
}
