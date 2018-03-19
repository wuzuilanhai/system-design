package com.biubiu.abc;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author 张海彪
 * @create 2018-03-18 下午4:52
 */
public class ABC {

    private static ReentrantLock lock = new ReentrantLock();

    private static Condition a = lock.newCondition();

    private static Condition b = lock.newCondition();

    private static Condition c = lock.newCondition();

    public static void main(String[] args) throws InterruptedException {
        Thread t1 = new Thread(new A());
        Thread t2 = new Thread(new B());
        Thread t3 = new Thread(new C());
        t2.start();
        t3.start();
        Thread.sleep(100);
        t1.start();
    }

    static class A implements Runnable {

        @Override
        public void run() {
            int i = 0;
            while (true) {
                lock.lock();
                try {
                    if (i == 0) {
                        i++;
                    } else {
                        a.await();
                    }
                    System.out.println("A");
                    Thread.sleep(1000);
                    b.signal();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } finally {
                    lock.unlock();
                }
            }
        }

    }

    static class B implements Runnable {

        @Override
        public void run() {
            while (true) {
                lock.lock();
                try {
                    b.await();
                    System.out.println("B");
                    Thread.sleep(1000);
                    c.signal();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } finally {
                    lock.unlock();
                }
            }
        }

    }

    static class C implements Runnable {

        @Override
        public void run() {
            while (true) {
                lock.lock();
                try {
                    c.await();
                    System.out.println("C");
                    Thread.sleep(1000);
                    a.signal();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } finally {
                    lock.unlock();
                }
            }
        }

    }

}
