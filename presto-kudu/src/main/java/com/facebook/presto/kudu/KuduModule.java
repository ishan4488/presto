/*
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
package com.facebook.presto.kudu;

import com.facebook.presto.kudu.procedures.RangePartitionProcedures;
import com.facebook.presto.kudu.properties.KuduTableProperties;
import com.facebook.presto.kudu.schema.NoSchemaEmulation;
import com.facebook.presto.kudu.schema.SchemaEmulation;
import com.facebook.presto.kudu.schema.SchemaEmulationByTableNameConvention;
import com.facebook.presto.spi.connector.ConnectorPageSinkProvider;
import com.facebook.presto.spi.connector.ConnectorPageSourceProvider;
import com.facebook.presto.spi.connector.ConnectorRecordSetProvider;
import com.facebook.presto.spi.connector.ConnectorSplitManager;
import com.facebook.presto.spi.procedure.Procedure;
import com.facebook.presto.spi.type.TypeManager;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.multibindings.MultibindingsScanner;
import com.google.inject.multibindings.ProvidesIntoSet;
import org.apache.kudu.client.KuduClient;

import javax.inject.Singleton;

import static io.airlift.configuration.ConfigBinder.configBinder;
import static java.util.Objects.requireNonNull;

public class KuduModule
        extends AbstractModule
{
    private final String connectorId;
    private final TypeManager typeManager;

    public KuduModule(String connectorId, TypeManager typeManager)
    {
        this.connectorId = requireNonNull(connectorId, "connector id is null");
        this.typeManager = requireNonNull(typeManager, "typeManager is null");
    }

    @Override
    protected void configure()
    {
        install(MultibindingsScanner.asModule());

        bind(TypeManager.class).toInstance(typeManager);

        bind(KuduConnector.class).in(Scopes.SINGLETON);
        bind(KuduConnectorId.class).toInstance(new KuduConnectorId(connectorId));
        bind(KuduMetadata.class).in(Scopes.SINGLETON);
        bind(KuduTableProperties.class).in(Scopes.SINGLETON);
        bind(ConnectorSplitManager.class).to(KuduSplitManager.class).in(Scopes.SINGLETON);
        bind(ConnectorRecordSetProvider.class).to(KuduRecordSetProvider.class)
                .in(Scopes.SINGLETON);
        bind(ConnectorPageSourceProvider.class).to(KuduPageSourceProvider.class)
                .in(Scopes.SINGLETON);
        bind(ConnectorPageSinkProvider.class).to(KuduPageSinkProvider.class).in(Scopes.SINGLETON);
        bind(KuduHandleResolver.class).in(Scopes.SINGLETON);
        bind(KuduRecordSetProvider.class).in(Scopes.SINGLETON);
        configBinder(binder()).bindConfig(KuduClientConfig.class);

        bind(RangePartitionProcedures.class).in(Scopes.SINGLETON);
        Multibinder.newSetBinder(binder(), Procedure.class);
    }

    @ProvidesIntoSet
    Procedure getAddRangePartitionProcedure(RangePartitionProcedures procedures)
    {
        return procedures.getAddPartitionProcedure();
    }

    @ProvidesIntoSet
    Procedure getDropRangePartitionProcedure(RangePartitionProcedures procedures)
    {
        return procedures.getDropPartitionProcedure();
    }

    @Singleton
    @Provides
    KuduClientSession createKuduClientSession(
            KuduConnectorId connectorId,
            KuduClientConfig config)
    {
        requireNonNull(config, "config is null");

        KuduClient.KuduClientBuilder builder = new KuduClient.KuduClientBuilder(config.getMasterAddresses());
        builder.defaultAdminOperationTimeoutMs(config.getDefaultAdminOperationTimeout().toMillis());
        builder.defaultOperationTimeoutMs(config.getDefaultOperationTimeout().toMillis());
        builder.defaultSocketReadTimeoutMs(config.getDefaultSocketReadTimeout().toMillis());
        if (config.isDisableStatistics()) {
            builder.disableStatistics();
        }
        KuduClient client = builder.build();

        SchemaEmulation strategy;
        if (config.isSchemaEmulationEnabled()) {
            strategy = new SchemaEmulationByTableNameConvention(config.getSchemaEmulationPrefix());
        }
        else {
            strategy = new NoSchemaEmulation();
        }
        return new KuduClientSession(connectorId, client, strategy);
    }
}
