@chcp 65001 > nul
cd C:\Users\15614\Documents\code\agent4j && git add -A && git commit -m "feat(workspace): add SharedWorkspace core storage class

- Thread-safe shared workspace supporting KV and document storage
- KV operations: writeKV, readKV, getKVBucket with versioning and eviction
- Document operations: writeDoc, readDoc with versioning and eviction
- General operations: delete, listKeys, clear, size, getEventBus
- LRU-like eviction strategy based on createdAt timestamp
- Uses ConcurrentHashMap for thread safety and ReadWriteLock for batch ops
- Integrated with WorkspaceEventBus for publish/subscribe events"