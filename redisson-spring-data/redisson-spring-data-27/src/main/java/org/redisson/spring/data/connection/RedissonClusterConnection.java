/**
 * Copyright (c) 2013-2024 Nikita Koksharov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.redisson.spring.data.connection;

import org.redisson.api.BatchResult;
import org.redisson.api.RFuture;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisClient;
import org.redisson.client.codec.ByteArrayCodec;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.RedisStrictCommand;
import org.redisson.client.protocol.decoder.ListScanResult;
import org.redisson.client.protocol.decoder.ObjectDecoder;
import org.redisson.client.protocol.decoder.ObjectListReplayDecoder;
import org.redisson.client.protocol.decoder.StringMapDataDecoder;
import org.redisson.command.CommandBatchService;
import org.redisson.connection.ClientConnectionsEntry;
import org.redisson.connection.MasterSlaveEntry;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.data.redis.connection.ClusterInfo;
import org.springframework.data.redis.connection.DefaultedRedisClusterConnection;
import org.springframework.data.redis.connection.RedisClusterNode;
import org.springframework.data.redis.connection.RedisClusterNode.SlotRange;
import org.springframework.data.redis.connection.convert.Converters;
import org.springframework.data.redis.connection.convert.StringToRedisClientInfoConverter;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanCursor;
import org.springframework.data.redis.core.ScanIteration;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.types.RedisClientInfo;
import org.springframework.util.Assert;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RedissonClusterConnection extends RedissonConnection implements DefaultedRedisClusterConnection {

    private static final RedisStrictCommand<List<RedisClusterNode>> CLUSTER_NODES = 
                            new RedisStrictCommand<List<RedisClusterNode>>("CLUSTER", "NODES", new ObjectDecoder(new RedisClusterNodeDecoder()));
    
    public RedissonClusterConnection(RedissonClient redisson) {
        super(redisson);
    }

    @Override
    public Iterable<RedisClusterNode> clusterGetNodes() {
        return read(null, StringCodec.INSTANCE, CLUSTER_NODES);
    }

    @Override
    public Collection<RedisClusterNode> clusterGetSlaves(RedisClusterNode master) {
        Iterable<RedisClusterNode> res = clusterGetNodes();
        RedisClusterNode masterNode = null;
        for (Iterator<RedisClusterNode> iterator = res.iterator(); iterator.hasNext();) {
            RedisClusterNode redisClusterNode = iterator.next();
            if (master.getHost().equals(redisClusterNode.getHost()) 
                    && master.getPort().equals(redisClusterNode.getPort())) {
                masterNode = redisClusterNode;
                break;
            }
        }
        
        if (masterNode == null) {
            throw new IllegalStateException("Unable to find master node: " + master);
        }
        
        for (Iterator<RedisClusterNode> iterator = res.iterator(); iterator.hasNext();) {
            RedisClusterNode redisClusterNode = iterator.next();
            if (redisClusterNode.getMasterId() == null 
                    || !redisClusterNode.getMasterId().equals(masterNode.getId())) {
                iterator.remove();
            }
        }
        return (Collection<RedisClusterNode>) res;
    }

    @Override
    public Map<RedisClusterNode, Collection<RedisClusterNode>> clusterGetMasterSlaveMap() {
        Iterable<RedisClusterNode> res = clusterGetNodes();
        
        Set<RedisClusterNode> masters = new HashSet<RedisClusterNode>();
        for (Iterator<RedisClusterNode> iterator = res.iterator(); iterator.hasNext();) {
            RedisClusterNode redisClusterNode = iterator.next();
            if (redisClusterNode.isMaster()) {
                masters.add(redisClusterNode);
            }
        }
        
        Map<RedisClusterNode, Collection<RedisClusterNode>> result = new HashMap<RedisClusterNode, Collection<RedisClusterNode>>();
        for (Iterator<RedisClusterNode> iterator = res.iterator(); iterator.hasNext();) {
            RedisClusterNode redisClusterNode = iterator.next();
            
            for (RedisClusterNode masterNode : masters) {
                if (redisClusterNode.getMasterId() != null 
                        && redisClusterNode.getMasterId().equals(masterNode.getId())) {
                    Collection<RedisClusterNode> list = result.get(masterNode);
                    if (list == null) {
                        list = new ArrayList<RedisClusterNode>();
                        result.put(masterNode, list);
                    }
                    list.add(redisClusterNode);
                }
            }
        }
        return result;
    }

    @Override
    public Integer clusterGetSlotForKey(byte[] key) {
        RFuture<Integer> f = executorService.readAsync((String)null, StringCodec.INSTANCE, RedisCommands.KEYSLOT, key);
        return syncFuture(f);
    }

    @Override
    public RedisClusterNode clusterGetNodeForSlot(int slot) {
        Iterable<RedisClusterNode> res = clusterGetNodes();
        for (RedisClusterNode redisClusterNode : res) {
            if (redisClusterNode.isMaster() && redisClusterNode.getSlotRange().contains(slot)) {
                return redisClusterNode;
            }
        }
        return null;
    }

    @Override
    public RedisClusterNode clusterGetNodeForKey(byte[] key) {
        int slot = executorService.getConnectionManager().calcSlot(key);
        return clusterGetNodeForSlot(slot);
    }

    @Override
    public ClusterInfo clusterGetClusterInfo() {
        RFuture<Map<String, String>> f = executorService.readAsync((String)null, StringCodec.INSTANCE, RedisCommands.CLUSTER_INFO);
        Map<String, String> entries = syncFuture(f);

        Properties props = new Properties();
        for (Entry<String, String> entry : entries.entrySet()) {
            props.setProperty(entry.getKey(), entry.getValue());
        }
        return new ClusterInfo(props);
    }

    @Override
    public void clusterAddSlots(RedisClusterNode node, int... slots) {
        RedisClient entry = getEntry(node);
        List<Integer> params = convert(slots);
        RFuture<Map<String, String>> f = executorService.writeAsync(entry, StringCodec.INSTANCE, RedisCommands.CLUSTER_ADDSLOTS, params.toArray());
        syncFuture(f);
    }

    protected List<Integer> convert(int... slots) {
        List<Integer> params = new ArrayList<Integer>();
        for (int slot : slots) {
            params.add(slot);
        }
        return params;
    }

    @Override
    public void clusterAddSlots(RedisClusterNode node, SlotRange range) {
        clusterAddSlots(node, range.getSlotsArray());
    }

    @Override
    public Long clusterCountKeysInSlot(int slot) {
        RedisClusterNode node = clusterGetNodeForSlot(slot);
        MasterSlaveEntry entry = executorService.getConnectionManager().getEntry(new InetSocketAddress(node.getHost(), node.getPort()));
        RFuture<Long> f = executorService.readAsync(entry, StringCodec.INSTANCE, RedisCommands.CLUSTER_COUNTKEYSINSLOT, slot);
        return syncFuture(f);
    }

    @Override
    public void clusterDeleteSlots(RedisClusterNode node, int... slots) {
        RedisClient entry = getEntry(node);
        List<Integer> params = convert(slots);
        RFuture<Long> f = executorService.writeAsync(entry, StringCodec.INSTANCE, RedisCommands.CLUSTER_DELSLOTS, params.toArray());
        syncFuture(f);
    }

    @Override
    public void clusterDeleteSlotsInRange(RedisClusterNode node, SlotRange range) {
        clusterDeleteSlots(node, range.getSlotsArray());
    }

    @Override
    public void clusterForget(RedisClusterNode node) {
        RFuture<Void> f = executorService.writeAsync((String)null, StringCodec.INSTANCE, RedisCommands.CLUSTER_FORGET, node.getId());
        syncFuture(f);
    }

    @Override
    public void clusterMeet(RedisClusterNode node) {
        Assert.notNull(node, "Cluster node must not be null for CLUSTER MEET command!");
        Assert.hasText(node.getHost(), "Node to meet cluster must have a host!");
        Assert.isTrue(node.getPort() > 0, "Node to meet cluster must have a port greater 0!");
        
        RFuture<Void> f = executorService.writeAsync((String)null, StringCodec.INSTANCE, RedisCommands.CLUSTER_MEET, node.getHost(), node.getPort());
        syncFuture(f);
    }

    @Override
    public void clusterSetSlot(RedisClusterNode node, int slot, AddSlots mode) {
        RedisClient entry = getEntry(node);
        RFuture<Map<String, String>> f = executorService.writeAsync(entry, StringCodec.INSTANCE, RedisCommands.CLUSTER_SETSLOT, slot, mode);
        syncFuture(f);
    }
    
    private static final RedisStrictCommand<List<String>> CLUSTER_GETKEYSINSLOT = new RedisStrictCommand<List<String>>("CLUSTER", "GETKEYSINSLOT", new ObjectListReplayDecoder<String>());

    @Override
    public List<byte[]> clusterGetKeysInSlot(int slot, Integer count) {
        RFuture<List<byte[]>> f = executorService.readAsync((String)null, ByteArrayCodec.INSTANCE, CLUSTER_GETKEYSINSLOT, slot, count);
        return syncFuture(f);
    }

    @Override
    public void clusterReplicate(RedisClusterNode master, RedisClusterNode slave) {
        RedisClient entry = getEntry(master);
        RFuture<Long> f = executorService.writeAsync(entry, StringCodec.INSTANCE, RedisCommands.CLUSTER_REPLICATE, slave.getId());
        syncFuture(f);
    }

    @Override
    public String ping(RedisClusterNode node) {
        return execute(node, RedisCommands.PING);
    }

    @Override
    public void bgReWriteAof(RedisClusterNode node) {
        execute(node, RedisCommands.BGREWRITEAOF);
    }

    @Override
    public void bgSave(RedisClusterNode node) {
        execute(node, RedisCommands.BGSAVE);
    }

    @Override
    public Long lastSave(RedisClusterNode node) {
        return execute(node, RedisCommands.LASTSAVE);
    }

    @Override
    public void save(RedisClusterNode node) {
        execute(node, RedisCommands.SAVE);
    }

    @Override
    public Long dbSize(RedisClusterNode node) {
        return execute(node, RedisCommands.DBSIZE);
    }

    private <T> T execute(RedisClusterNode node, RedisCommand<T> command) {
        RedisClient entry = getEntry(node);
        RFuture<T> f = executorService.writeAsync(entry, StringCodec.INSTANCE, command);
        return syncFuture(f);
    }

    protected RedisClient getEntry(RedisClusterNode node) {
        InetSocketAddress addr = new InetSocketAddress(node.getHost(), node.getPort());
        MasterSlaveEntry entry = executorService.getConnectionManager().getEntry(addr);
        ClientConnectionsEntry e = entry.getEntry(addr);
        return e.getClient();
    }

    @Override
    public void flushDb(RedisClusterNode node) {
        execute(node, RedisCommands.FLUSHDB);
    }

    @Override
    public void flushAll(RedisClusterNode node) {
        execute(node, RedisCommands.FLUSHALL);
    }

    @Override
    public Properties info(RedisClusterNode node) {
        Map<String, String> info = execute(node, RedisCommands.INFO_ALL);
        Properties result = new Properties();
        for (Entry<String, String> entry : info.entrySet()) {
            result.setProperty(entry.getKey(), entry.getValue());
        }
        return result;
    }

    @Override
    public Properties info(RedisClusterNode node, String section) {
        RedisStrictCommand<Map<String, String>> command = new RedisStrictCommand<Map<String, String>>("INFO", section, new StringMapDataDecoder());

        Map<String, String> info = execute(node, command);
        Properties result = new Properties();
        for (Entry<String, String> entry : info.entrySet()) {
            result.setProperty(entry.getKey(), entry.getValue());
        }
        return result;
    }

    private final RedisStrictCommand<List<byte[]>> KEYS = new RedisStrictCommand<>("KEYS");

    @Override
    public Set<byte[]> keys(RedisClusterNode node, byte[] pattern) {
        RedisClient entry = getEntry(node);
        RFuture<Collection<byte[]>> f = executorService.readAsync(entry, ByteArrayCodec.INSTANCE, KEYS, pattern);
        Collection<byte[]> keys = syncFuture(f);
        return new HashSet<>(keys);
    }

    @Override
    public byte[] randomKey(RedisClusterNode node) {
        RedisClient entry = getEntry(node);
        RFuture<byte[]> f = executorService.readRandomAsync(entry, ByteArrayCodec.INSTANCE, RedisCommands.RANDOM_KEY);
        return syncFuture(f);
    }

    @Override
    public void shutdown(RedisClusterNode node) {
        RedisClient entry = getEntry(node);
        RFuture<Void> f = executorService.readAsync(entry, ByteArrayCodec.INSTANCE, RedisCommands.SHUTDOWN);
        syncFuture(f);
    }

    @Override
    public Properties getConfig(RedisClusterNode node, String pattern) {
        RedisClient entry = getEntry(node);
        RFuture<List<String>> f = executorService.writeAsync(entry, StringCodec.INSTANCE, RedisCommands.CONFIG_GET, pattern);
        List<String> r = syncFuture(f);
        if (r != null) {
            return Converters.toProperties(r);
        }
        return null;
    }

    @Override
    public void setConfig(RedisClusterNode node, String param, String value) {
        RedisClient entry = getEntry(node);
        RFuture<Void> f = executorService.writeAsync(entry, StringCodec.INSTANCE, RedisCommands.CONFIG_SET, param, value);
        syncFuture(f);
    }

    @Override
    public void resetConfigStats(RedisClusterNode node) {
        RedisClient entry = getEntry(node);
        RFuture<Void> f = executorService.writeAsync(entry, StringCodec.INSTANCE, RedisCommands.CONFIG_RESETSTAT);
        syncFuture(f);
    }

    @Override
    public Long time(RedisClusterNode node) {
        RedisClient entry = getEntry(node);
        RFuture<Long> f = executorService.readAsync(entry, LongCodec.INSTANCE, RedisCommands.TIME_LONG);
        return syncFuture(f);
    }

    private static final StringToRedisClientInfoConverter CONVERTER = new StringToRedisClientInfoConverter();
    
    @Override
    public List<RedisClientInfo> getClientList(RedisClusterNode node) {
        RedisClient entry = getEntry(node);
        RFuture<List<String>> f = executorService.readAsync(entry, StringCodec.INSTANCE, RedisCommands.CLIENT_LIST);
        List<String> list = syncFuture(f);
        return CONVERTER.convert(list.toArray(new String[list.size()]));
    }

    @Override
    public Cursor<byte[]> scan(RedisClusterNode node, ScanOptions options) {
        return new ScanCursor<byte[]>(0, options) {

            private RedisClient client = getEntry(node);
            
            @Override
            protected ScanIteration<byte[]> doScan(long cursorId, ScanOptions options) {
                if (isQueueing() || isPipelined()) {
                    throw new UnsupportedOperationException("'SSCAN' cannot be called in pipeline / transaction mode.");
                }

                if (client == null) {
                    return null;
                }

                List<Object> args = new ArrayList<Object>();
                if (cursorId == 101010101010101010L) {
                    cursorId = 0;
                }
                args.add(Long.toUnsignedString(cursorId));
                if (options.getPattern() != null) {
                    args.add("MATCH");
                    args.add(options.getPattern());
                }
                if (options.getCount() != null) {
                    args.add("COUNT");
                    args.add(options.getCount());
                }

                RFuture<ListScanResult<byte[]>> f = executorService.readAsync(client, ByteArrayCodec.INSTANCE, RedisCommands.SCAN, args.toArray());
                ListScanResult<byte[]> res = syncFuture(f);
                String pos = res.getPos();
                client = res.getRedisClient();
                if ("0".equals(pos)) {
                    client = null;
                }

                return new ScanIteration<byte[]>(Long.parseUnsignedLong(pos), res.getValues());
            }
        }.open();
    }

    @Override
    public void rename(byte[] oldName, byte[] newName) {

        if (isPipelined()) {
            throw new InvalidDataAccessResourceUsageException("Clustered rename is not supported in a pipeline");
        }

        if (executorService.getConnectionManager().calcSlot(oldName) == executorService.getConnectionManager().calcSlot(newName)) {
            super.rename(oldName, newName);
            return;
        }

        final byte[] value = dump(oldName);

        if (null != value) {

            final Long sourceTtlInSeconds = ttl(oldName);

            final long ttlInMilliseconds;
            if (null != sourceTtlInSeconds && sourceTtlInSeconds > 0) {
                ttlInMilliseconds = sourceTtlInSeconds * 1000;
            } else {
                ttlInMilliseconds = 0;
            }

            restore(newName, ttlInMilliseconds, value);
            del(oldName);
        }
    }

    @Override
    public Boolean renameNX(byte[] oldName, byte[] newName) {
        if (isPipelined()) {
            throw new InvalidDataAccessResourceUsageException("Clustered rename is not supported in a pipeline");
        }

        if (executorService.getConnectionManager().calcSlot(oldName) == executorService.getConnectionManager().calcSlot(newName)) {
            return super.renameNX(oldName, newName);
        }

        final byte[] value = dump(oldName);

        if (null != value && !exists(newName)) {

            final Long sourceTtlInSeconds = ttl(oldName);

            final long ttlInMilliseconds;
            if (null != sourceTtlInSeconds && sourceTtlInSeconds > 0) {
                ttlInMilliseconds = sourceTtlInSeconds * 1000;
            } else {
                ttlInMilliseconds = 0;
            }

            restore(newName, ttlInMilliseconds, value);
            del(oldName);

            return true;
        }

        return false;
    }

    @Override
    public Long del(byte[]... keys) {
        if (isQueueing() || isPipelined()) {
            for (byte[] key: keys) {
                write(key, LongCodec.INSTANCE, RedisCommands.DEL, key);
            }

            return null;
        }

        CommandBatchService es = new CommandBatchService(executorService);
        for (byte[] key: keys) {
            es.writeAsync(key, StringCodec.INSTANCE, RedisCommands.DEL, key);
        }
        BatchResult<Long> b = (BatchResult<Long>) es.execute();
        return b.getResponses().stream().collect(Collectors.summarizingLong(v -> v)).getSum();
    }

    @Override
    public List<byte[]> mGet(byte[]... keys) {
        if (isQueueing() || isPipelined()) {
            for (byte[] key : keys) {
                read(key, ByteArrayCodec.INSTANCE, RedisCommands.GET, key);
            }
            return null;
        }

        CommandBatchService es = new CommandBatchService(executorService);
        for (byte[] key: keys) {
            es.readAsync(key, ByteArrayCodec.INSTANCE, RedisCommands.GET, key);
        }
        BatchResult<byte[]> r = (BatchResult<byte[]>) es.execute();
        return r.getResponses();
    }

    @Override
    public Boolean mSet(Map<byte[], byte[]> tuple) {
        if (isQueueing() || isPipelined()) {
            for (Entry<byte[], byte[]> entry: tuple.entrySet()) {
                write(entry.getKey(), StringCodec.INSTANCE, RedisCommands.SET, entry.getKey(), entry.getValue());
            }
            return true;
        }

        CommandBatchService es = new CommandBatchService(executorService);
        for (Entry<byte[], byte[]> entry: tuple.entrySet()) {
            es.writeAsync(entry.getKey(), StringCodec.INSTANCE, RedisCommands.SET, entry.getKey(), entry.getValue());
        }
        es.execute();
        return true;
    }

}
