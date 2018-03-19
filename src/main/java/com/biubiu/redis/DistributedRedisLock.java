package com.biubiu.redis;

import redis.clients.jedis.Jedis;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.LockSupport;

/**
 * @author 张海彪
 * @create 2018-03-19 上午9:26
 */
public class DistributedRedisLock implements Lock {

    private static final long DEFAULT_TIMEOUT = 1000L;

    private static final int RETRY_TIMES = 3;

    private static final long PARK_TIME = 200L;

    private static final long SPIN_FOR_TIMEOUT_THRESHOLD = 1000L;

    private static final String NX = "NX";

    private static final String PX = "PX";

    private static final String OK = "OK";

    private static final String UNLOCK_SCRIPT =
            "if redis.call('get',KEYS[1])==ARGV[1] " +
                    "then return redis.call('del',KEYS[1]) " +
                    "else return 0 end";

    private long redisLockTimeout = DEFAULT_TIMEOUT;

    private final Sync sync = new Sync();

    private final UUID uuid = UUID.randomUUID();

    private final String valueFormat = "%d:" + uuid.toString();

    private final String key;

    public DistributedRedisLock(String key) {
        this.key = key;
    }

    public DistributedRedisLock(String key, long redisLockTimeout) {
        this(key);
        this.redisLockTimeout = redisLockTimeout;
    }

    public void setRedisLockTimeout(long redisLockTimeout) {
        this.redisLockTimeout = redisLockTimeout;
    }

    @Override
    public void lock() {
        sync.lock();
    }

    @Override
    public void lockInterruptibly() throws InterruptedException {
        sync.acquireInterruptibly(1);
    }

    @Override
    public boolean tryLock() {
        return sync.tryAcquire(1);
    }

    @Override
    public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
        return sync.tryAcquireNanos(1, unit.toNanos(time));
    }

    @Override
    public void unlock() {
        sync.release(1);
    }

    @Override
    public Condition newCondition() {
        return sync.newCondition();
    }

    class Sync extends AbstractQueuedSynchronizer {

        @Override
        protected boolean tryAcquire(int acquires) {
            final Thread current = Thread.currentThread();
            int c = getState();
            if (c == 0) {
                if (!hasQueuedPredecessors() && compareAndSetState(0, 1)) {
                    setExclusiveOwnerThread(current);
                    return tryAcquireRedisLock(TimeUnit.MILLISECONDS.toNanos(redisLockTimeout));
                }
            } else if (current == getExclusiveOwnerThread()) {
                int nextc = c + acquires;
                if (nextc < 0)
                    throw new Error("Maximum lock count exceeded");
                setState(nextc);
                return true;
            }
            return false;
        }

        private boolean tryAcquireRedisLock(long nanosTimeout) {
            if (nanosTimeout <= 0L)
                return false;
            final long deadline = System.nanoTime() + nanosTimeout;
            int count = 0;
            boolean interrupted = false;
            Jedis jedis = null;
            try {
                jedis = new Jedis("127.0.0.1", 6379);
                while (true) {
                    nanosTimeout = deadline - System.nanoTime();
                    if (nanosTimeout <= 0L)
                        throw new AcquireLockTimeoutException();
                    String value = String.format(valueFormat, Thread.currentThread().getId());
                    String response = jedis.set(key, value, NX, PX, redisLockTimeout);
                    if (OK.equals(response))
                        return !interrupted;
                    if (count > RETRY_TIMES && nanosTimeout > SPIN_FOR_TIMEOUT_THRESHOLD && parkAndCheckInterrupt())
                        interrupted = true;
                    count++;
                }
            } finally {
                if (jedis != null)
                    jedis.close();
            }
        }

        final boolean parkAndCheckInterrupt() {
            LockSupport.parkNanos(PARK_TIME);
            return Thread.interrupted();
        }

        @Override
        protected boolean tryRelease(int releases) {
            int c = getState() - releases;
            if (Thread.currentThread() != getExclusiveOwnerThread())
                throw new IllegalMonitorStateException();
            boolean free = false;
            if (c == 0) {
                Jedis jedis = null;
                try {
                    jedis = new Jedis("127.0.0.1", 6379);
                    String value = String.format(valueFormat, Thread.currentThread().getId());
                    jedis.eval(UNLOCK_SCRIPT, Arrays.asList(key), Arrays.asList(value));
                } finally {
                    if (jedis != null)
                        jedis.close();
                }
                free = true;
                setExclusiveOwnerThread(null);
            }
            setState(c);
            return free;
        }

        final void lock() {
            if (compareAndSetState(0, 1))
                setExclusiveOwnerThread(Thread.currentThread());
            else
                acquire(1);
        }

        protected final boolean isHeldExclusively() {
            return getExclusiveOwnerThread() == Thread.currentThread();
        }

        final Thread getOwner() {
            return getState() == 0 ? null : getExclusiveOwnerThread();
        }

        final int getHoldCount() {
            return isHeldExclusively() ? getState() : 0;
        }

        final boolean isLocked() {
            return getState() != 0;
        }

        final ConditionObject newCondition() {
            return new ConditionObject();
        }

        private void readObject(ObjectInputStream s)
                throws IOException, ClassNotFoundException {
            s.defaultReadObject();
            setState(0);
        }

    }

}
