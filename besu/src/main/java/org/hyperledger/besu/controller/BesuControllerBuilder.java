/*
 * Copyright ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.controller;

import static com.google.common.base.Preconditions.checkNotNull;

import org.hyperledger.besu.config.GenesisConfigFile;
import org.hyperledger.besu.consensus.qbft.pki.PkiBlockCreationConfiguration;
import org.hyperledger.besu.crypto.NodeKey;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.GasLimitCalculator;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.methods.JsonRpcMethods;
import org.hyperledger.besu.ethereum.blockcreation.MiningCoordinator;
import org.hyperledger.besu.ethereum.bonsai.BonsaiWorldStateArchive;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.chain.BlockchainStorage;
import org.hyperledger.besu.ethereum.chain.DefaultBlockchain;
import org.hyperledger.besu.ethereum.chain.GenesisState;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.MiningParameters;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.core.Synchronizer;
import org.hyperledger.besu.ethereum.eth.EthProtocol;
import org.hyperledger.besu.ethereum.eth.EthProtocolConfiguration;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthMessages;
import org.hyperledger.besu.ethereum.eth.manager.EthPeers;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManager;
import org.hyperledger.besu.ethereum.eth.manager.EthScheduler;
import org.hyperledger.besu.ethereum.eth.peervalidation.ClassicForkPeerValidator;
import org.hyperledger.besu.ethereum.eth.peervalidation.DaoForkPeerValidator;
import org.hyperledger.besu.ethereum.eth.peervalidation.PeerValidator;
import org.hyperledger.besu.ethereum.eth.peervalidation.RequiredBlocksPeerValidator;
import org.hyperledger.besu.ethereum.eth.sync.DefaultSynchronizer;
import org.hyperledger.besu.ethereum.eth.sync.SyncMode;
import org.hyperledger.besu.ethereum.eth.sync.SynchronizerConfiguration;
import org.hyperledger.besu.ethereum.eth.sync.state.SyncState;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolFactory;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.p2p.config.SubProtocolConfiguration;
import org.hyperledger.besu.ethereum.storage.StorageProvider;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier;
import org.hyperledger.besu.ethereum.worldstate.DataStorageConfiguration;
import org.hyperledger.besu.ethereum.worldstate.DataStorageFormat;
import org.hyperledger.besu.ethereum.worldstate.DefaultWorldStateArchive;
import org.hyperledger.besu.ethereum.worldstate.MarkSweepPruner;
import org.hyperledger.besu.ethereum.worldstate.Pruner;
import org.hyperledger.besu.ethereum.worldstate.PrunerConfiguration;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.ethereum.worldstate.WorldStatePreimageStorage;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.metrics.ObservableMetricsSystem;
import org.hyperledger.besu.plugin.services.permissioning.NodeMessagePermissioningProvider;

import java.io.Closeable;
import java.math.BigInteger;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public abstract class BesuControllerBuilder {
  private static final Logger LOG = LogManager.getLogger();

  protected GenesisConfigFile genesisConfig;
  private SynchronizerConfiguration syncConfig;
  private EthProtocolConfiguration ethereumWireProtocolConfiguration;
  protected TransactionPoolConfiguration transactionPoolConfiguration;
  protected BigInteger networkId;
  protected MiningParameters miningParameters;
  protected ObservableMetricsSystem metricsSystem;
  protected PrivacyParameters privacyParameters;
  protected Optional<PkiBlockCreationConfiguration> pkiBlockCreationConfiguration =
      Optional.empty();
  protected Path dataDirectory;
  protected Clock clock;
  protected NodeKey nodeKey;
  protected boolean isRevertReasonEnabled;
  GasLimitCalculator gasLimitCalculator;
  private StorageProvider storageProvider;
  private boolean isPruningEnabled;
  private PrunerConfiguration prunerConfiguration;
  Map<String, String> genesisConfigOverrides;
  private Map<Long, Hash> requiredBlocks = Collections.emptyMap();
  private long reorgLoggingThreshold;
  private DataStorageConfiguration dataStorageConfiguration =
      DataStorageConfiguration.DEFAULT_CONFIG;
  private List<NodeMessagePermissioningProvider> messagePermissioningProviders =
      Collections.emptyList();
  protected EvmConfiguration evmConfiguration;

  public BesuControllerBuilder storageProvider(final StorageProvider storageProvider) {
    this.storageProvider = storageProvider;
    return this;
  }

  public BesuControllerBuilder genesisConfigFile(final GenesisConfigFile genesisConfig) {
    this.genesisConfig = genesisConfig;
    return this;
  }

  public BesuControllerBuilder synchronizerConfiguration(
      final SynchronizerConfiguration synchronizerConfig) {
    this.syncConfig = synchronizerConfig;
    return this;
  }

  public BesuControllerBuilder ethProtocolConfiguration(
      final EthProtocolConfiguration ethProtocolConfiguration) {
    this.ethereumWireProtocolConfiguration = ethProtocolConfiguration;
    return this;
  }

  public BesuControllerBuilder networkId(final BigInteger networkId) {
    this.networkId = networkId;
    return this;
  }

  public BesuControllerBuilder miningParameters(final MiningParameters miningParameters) {
    this.miningParameters = miningParameters;
    return this;
  }

  public BesuControllerBuilder messagePermissioningProviders(
      final List<NodeMessagePermissioningProvider> messagePermissioningProviders) {
    this.messagePermissioningProviders = messagePermissioningProviders;
    return this;
  }

  public BesuControllerBuilder nodeKey(final NodeKey nodeKey) {
    this.nodeKey = nodeKey;
    return this;
  }

  public BesuControllerBuilder metricsSystem(final ObservableMetricsSystem metricsSystem) {
    this.metricsSystem = metricsSystem;
    return this;
  }

  public BesuControllerBuilder privacyParameters(final PrivacyParameters privacyParameters) {
    this.privacyParameters = privacyParameters;
    return this;
  }

  public BesuControllerBuilder pkiBlockCreationConfiguration(
      final Optional<PkiBlockCreationConfiguration> pkiBlockCreationConfiguration) {
    this.pkiBlockCreationConfiguration = pkiBlockCreationConfiguration;
    return this;
  }

  public BesuControllerBuilder dataDirectory(final Path dataDirectory) {
    this.dataDirectory = dataDirectory;
    return this;
  }

  public BesuControllerBuilder clock(final Clock clock) {
    this.clock = clock;
    return this;
  }

  public BesuControllerBuilder transactionPoolConfiguration(
      final TransactionPoolConfiguration transactionPoolConfiguration) {
    this.transactionPoolConfiguration = transactionPoolConfiguration;
    return this;
  }

  public BesuControllerBuilder isRevertReasonEnabled(final boolean isRevertReasonEnabled) {
    this.isRevertReasonEnabled = isRevertReasonEnabled;
    return this;
  }

  public BesuControllerBuilder isPruningEnabled(final boolean isPruningEnabled) {
    this.isPruningEnabled = isPruningEnabled;
    return this;
  }

  public BesuControllerBuilder pruningConfiguration(final PrunerConfiguration prunerConfiguration) {
    this.prunerConfiguration = prunerConfiguration;
    return this;
  }

  public BesuControllerBuilder genesisConfigOverrides(
      final Map<String, String> genesisConfigOverrides) {
    this.genesisConfigOverrides = genesisConfigOverrides;
    return this;
  }

  public BesuControllerBuilder gasLimitCalculator(final GasLimitCalculator gasLimitCalculator) {
    this.gasLimitCalculator = gasLimitCalculator;
    return this;
  }

  public BesuControllerBuilder requiredBlocks(final Map<Long, Hash> requiredBlocks) {
    this.requiredBlocks = requiredBlocks;
    return this;
  }

  public BesuControllerBuilder reorgLoggingThreshold(final long reorgLoggingThreshold) {
    this.reorgLoggingThreshold = reorgLoggingThreshold;
    return this;
  }

  public BesuControllerBuilder dataStorageConfiguration(
      final DataStorageConfiguration dataStorageConfiguration) {
    this.dataStorageConfiguration = dataStorageConfiguration;
    return this;
  }

  public BesuControllerBuilder evmConfiguration(final EvmConfiguration evmConfiguration) {
    this.evmConfiguration = evmConfiguration;
    return this;
  }

  public BesuController build() {
    checkNotNull(genesisConfig, "Missing genesis config");
    checkNotNull(syncConfig, "Missing sync config");
    checkNotNull(ethereumWireProtocolConfiguration, "Missing ethereum protocol configuration");
    checkNotNull(networkId, "Missing network ID");
    checkNotNull(miningParameters, "Missing mining parameters");
    checkNotNull(metricsSystem, "Missing metrics system");
    checkNotNull(privacyParameters, "Missing privacy parameters");
    checkNotNull(dataDirectory, "Missing data directory"); // Why do we need this?
    checkNotNull(clock, "Missing clock");
    checkNotNull(transactionPoolConfiguration, "Missing transaction pool configuration");
    checkNotNull(nodeKey, "Missing node key");
    checkNotNull(storageProvider, "Must supply a storage provider");
    checkNotNull(gasLimitCalculator, "Missing gas limit calculator");
    checkNotNull(evmConfiguration, "Missing evm config");
    prepForBuild();

    final ProtocolSchedule protocolSchedule = createProtocolSchedule();
    final GenesisState genesisState = GenesisState.fromConfig(genesisConfig, protocolSchedule);
    final WorldStateStorage worldStateStorage =
        storageProvider.createWorldStateStorage(dataStorageConfiguration.getDataStorageFormat());

    final BlockchainStorage blockchainStorage =
        storageProvider.createBlockchainStorage(protocolSchedule);

    final MutableBlockchain blockchain =
        DefaultBlockchain.createMutable(
            genesisState.getBlock(), blockchainStorage, metricsSystem, reorgLoggingThreshold);

    final WorldStateArchive worldStateArchive =
        createWorldStateArchive(worldStateStorage, blockchain);
    final ProtocolContext protocolContext =
        ProtocolContext.init(
            blockchain,
            worldStateArchive,
            genesisState,
            protocolSchedule,
            this::createConsensusContext);
    validateContext(protocolContext);

    protocolSchedule.setPublicWorldStateArchiveForPrivacyBlockProcessor(
        protocolContext.getWorldStateArchive());

    Optional<Pruner> maybePruner = Optional.empty();
    if (isPruningEnabled) {
      if (!storageProvider.isWorldStateIterable()) {
        LOG.warn(
            "Cannot enable pruning with current database version. Disabling. Resync to get the latest database version or disable pruning explicitly on the command line to remove this warning.");
      } else if (dataStorageConfiguration.getDataStorageFormat().equals(DataStorageFormat.BONSAI)) {
        LOG.warn(
            "Cannot enable pruning with Bonsai data storage format. Disabling. Change the data storage format or disable pruning explicitly on the command line to remove this warning.");
      } else {
        maybePruner =
            Optional.of(
                new Pruner(
                    new MarkSweepPruner(
                        ((DefaultWorldStateArchive) worldStateArchive).getWorldStateStorage(),
                        blockchain,
                        storageProvider.getStorageBySegmentIdentifier(
                            KeyValueSegmentIdentifier.PRUNING_STATE),
                        metricsSystem),
                    blockchain,
                    prunerConfiguration));
      }
    }
    final EthPeers ethPeers =
        new EthPeers(getSupportedProtocol(), clock, metricsSystem, messagePermissioningProviders);

    final EthMessages ethMessages = new EthMessages();
    final EthScheduler scheduler =
        new EthScheduler(
            syncConfig.getDownloaderParallelism(),
            syncConfig.getTransactionsParallelism(),
            syncConfig.getComputationParallelism(),
            metricsSystem);
    final EthContext ethContext = new EthContext(ethPeers, ethMessages, scheduler);
    final SyncState syncState = new SyncState(blockchain, ethPeers);
    final boolean fastSyncEnabled = SyncMode.FAST.equals(syncConfig.getSyncMode());

    final TransactionPool transactionPool =
        TransactionPoolFactory.createTransactionPool(
            protocolSchedule,
            protocolContext,
            ethContext,
            clock,
            metricsSystem,
            syncState,
            miningParameters.getMinTransactionGasPrice(),
            transactionPoolConfiguration);

    final EthProtocolManager ethProtocolManager =
        createEthProtocolManager(
            protocolContext,
            fastSyncEnabled,
            transactionPool,
            ethereumWireProtocolConfiguration,
            ethPeers,
            ethContext,
            ethMessages,
            scheduler,
            createPeerValidators(protocolSchedule));

    final Synchronizer synchronizer =
        new DefaultSynchronizer(
            syncConfig,
            protocolSchedule,
            protocolContext,
            worldStateStorage,
            ethProtocolManager.getBlockBroadcaster(),
            maybePruner,
            ethProtocolManager.ethContext(),
            syncState,
            dataDirectory,
            clock,
            metricsSystem);

    final MiningCoordinator miningCoordinator =
        createMiningCoordinator(
            protocolSchedule,
            protocolContext,
            transactionPool,
            miningParameters,
            syncState,
            ethProtocolManager);

    final PluginServiceFactory additionalPluginServices =
        createAdditionalPluginServices(blockchain, protocolContext);

    final SubProtocolConfiguration subProtocolConfiguration =
        createSubProtocolConfiguration(ethProtocolManager);

    final JsonRpcMethods additionalJsonRpcMethodFactory =
        createAdditionalJsonRpcMethodFactory(protocolContext);

    final List<Closeable> closeables = new ArrayList<>();
    closeables.add(storageProvider);
    if (privacyParameters.getPrivateStorageProvider() != null) {
      closeables.add(privacyParameters.getPrivateStorageProvider());
    }

    return new BesuController(
        protocolSchedule,
        protocolContext,
        ethProtocolManager,
        genesisConfig.getConfigOptions(genesisConfigOverrides),
        subProtocolConfiguration,
        synchronizer,
        syncState,
        transactionPool,
        miningCoordinator,
        privacyParameters,
        miningParameters,
        additionalJsonRpcMethodFactory,
        nodeKey,
        closeables,
        additionalPluginServices);
  }

  protected void prepForBuild() {}

  protected JsonRpcMethods createAdditionalJsonRpcMethodFactory(
      final ProtocolContext protocolContext) {
    return apis -> Collections.emptyMap();
  }

  protected SubProtocolConfiguration createSubProtocolConfiguration(
      final EthProtocolManager ethProtocolManager) {
    return new SubProtocolConfiguration().withSubProtocol(EthProtocol.get(), ethProtocolManager);
  }

  protected abstract MiningCoordinator createMiningCoordinator(
      ProtocolSchedule protocolSchedule,
      ProtocolContext protocolContext,
      TransactionPool transactionPool,
      MiningParameters miningParameters,
      SyncState syncState,
      EthProtocolManager ethProtocolManager);

  protected abstract ProtocolSchedule createProtocolSchedule();

  protected void validateContext(final ProtocolContext context) {}

  protected abstract Object createConsensusContext(
      Blockchain blockchain,
      WorldStateArchive worldStateArchive,
      ProtocolSchedule protocolSchedule);

  protected String getSupportedProtocol() {
    return EthProtocol.NAME;
  }

  protected EthProtocolManager createEthProtocolManager(
      final ProtocolContext protocolContext,
      final boolean fastSyncEnabled,
      final TransactionPool transactionPool,
      final EthProtocolConfiguration ethereumWireProtocolConfiguration,
      final EthPeers ethPeers,
      final EthContext ethContext,
      final EthMessages ethMessages,
      final EthScheduler scheduler,
      final List<PeerValidator> peerValidators) {
    return new EthProtocolManager(
        protocolContext.getBlockchain(),
        networkId,
        protocolContext.getWorldStateArchive(),
        transactionPool,
        ethereumWireProtocolConfiguration,
        ethPeers,
        ethMessages,
        ethContext,
        peerValidators,
        fastSyncEnabled,
        scheduler,
        genesisConfig.getForks());
  }

  private WorldStateArchive createWorldStateArchive(
      final WorldStateStorage worldStateStorage, final Blockchain blockchain) {
    switch (dataStorageConfiguration.getDataStorageFormat()) {
      case BONSAI:
        return new BonsaiWorldStateArchive(
            storageProvider, blockchain, dataStorageConfiguration.getBonsaiMaxLayersToLoad());
      case FOREST:
      default:
        final WorldStatePreimageStorage preimageStorage =
            storageProvider.createWorldStatePreimageStorage();
        return new DefaultWorldStateArchive(worldStateStorage, preimageStorage);
    }
  }

  private List<PeerValidator> createPeerValidators(final ProtocolSchedule protocolSchedule) {
    final List<PeerValidator> validators = new ArrayList<>();

    final OptionalLong daoBlock =
        genesisConfig.getConfigOptions(genesisConfigOverrides).getDaoForkBlock();
    if (daoBlock.isPresent()) {
      // Setup dao validator
      validators.add(
          new DaoForkPeerValidator(protocolSchedule, metricsSystem, daoBlock.getAsLong()));
    }

    final OptionalLong classicBlock =
        genesisConfig.getConfigOptions(genesisConfigOverrides).getClassicForkBlock();
    // setup classic validator
    if (classicBlock.isPresent()) {
      validators.add(
          new ClassicForkPeerValidator(protocolSchedule, metricsSystem, classicBlock.getAsLong()));
    }

    for (final Map.Entry<Long, Hash> requiredBlock : requiredBlocks.entrySet()) {
      validators.add(
          new RequiredBlocksPeerValidator(
              protocolSchedule, metricsSystem, requiredBlock.getKey(), requiredBlock.getValue()));
    }

    return validators;
  }

  protected abstract PluginServiceFactory createAdditionalPluginServices(
      final Blockchain blockchain, final ProtocolContext protocolContext);
}
