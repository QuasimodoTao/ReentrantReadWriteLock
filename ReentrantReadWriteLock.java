/*
	ReentrantReadWriteLock.java
	Copyright (C) 2020  Quasimodo 

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>.

*/

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

class UINT124 {
  /*This class support a 124-bits number, and some simple operate.Might some bugs are exist.*/
	long low;
	long high;
	public UINT124() {
		low = 0;
		high= 0;
	}
	public int add(long sum2) {
    //this += sum2
		if(sum2 < 0) return -1;
		low += sum2;
		if((low & 0x8000000000000000L) == 0x8000000000000000L) {//carry
			low &= 0x7fffffffffffffffL;
			high++;
		}
		if((high & 0x8000000000000000L) == 0x8000000000000000L) {//carry
			high &= 0x7fffffffffffffffL;
			return 1;
		}
		return 0;
	}
	public int sub(long sum2) {//this -= sum2
		if(sum2 < 0) return -1;
		if(low < sum2) {
			low += (0x7fffffffffffffL - sum2);
			low++;
			high--;
		}
		if((high & 0x8000000000000000L) == 0x8000000000000000L) {
			high &= 0x7fffffffffffffffL;
			return 1;
			
		}
		return 0;
	}
	public int mul(long sum1,long sum2) {
		long res0,res1,res2,res3;
	  //this = sum1 * sum2
		if(sum1 <0 || sum2 < 0) return -1;
		if(sum1 > 0x3fffffffffffffffL || 
				sum2 > 0x3fffffffffffffffL) return -1;
		res0 = (sum1 & 0x000000007fffffffL) * 
				(sum2 & 0x000000007fffffffL);
		res1 = (sum1 >> 31) * (sum2 & 0x000000007fffffffL);
		res2 = (sum1 & 0x000000007fffffffL) * (sum2 >> 31);
		res3 = (sum1 >> 31) * (sum2 >> 31);
		res1 += res2;
		res0 += (res1 << 31) & 0x3fffffff80000000L;
		if((res0 & 0x4000000000000000L) == 0x4000000000000000L) {//carry
			res3++;
			res0 &= 0x3fffffffffffffffL;
		}
		res1 >>= 31;
		res3 += res1;
		if((res3 & 1) == 1) {
			res0 |= 0x4000000000000000L;
		}
		res3 >>= 1;
		low = res0;
		high = res3;
		return 0;
	}
	public int cmp(UINT124 sum2) {
		if(high > sum2.high) {
			return 1;
		}
		if(high < sum2.high) {
			return -1;
		}
		if(low > sum2.low) {
			return 1;
		}
		if(low < sum2.low) {
			return -1;
		}
		return 0;
	}
}

public class ReentrantReadWriteLock {
	Lock lock = new ReentrantLock();
	int readers;      //count current readers
	int writers;      //Count current writers;
	boolean classLock;
	long ID_XOR;      //When got or release a reader lock, ID_XOR ^= TID;
	UINT124 ID_ADD = new UINT124();//When a reader got a lock, we + thread' ID to this class.
                                //Release a lock, we - thread' ID.
	long writerId;  //Hold current reader's TID which is holding reader-lock.
	Condition c_read;
	Condition c_write;
	
	public ReentrantReadWriteLock() {//a reentrant read and write lock
		readers = 0;
		writers = 0;
		classLock = false;
		ID_XOR = 0;
		writerId = 0;
		c_read = lock.newCondition();
		c_write = lock.newCondition();
	}
	private void i_lock() {//get lock for hole class
		boolean i_got;
		while(true) {//a TTASLock
			while(classLock) {}
			i_got = false;
			synchronized(this) {
				if(!classLock) {
					classLock = true;
					i_got = true;־
				}
			};
			if(!i_got) continue;
			return;
		}
	}
	private void i_unlock() {//release lock for hole class
		classLock = false;
	}
	public void ReadLock() throws InterruptedException{//
		long curThreadId;
		
		curThreadId = Thread.currentThread().getId();
		while(true) {
			i_lock();
			if(writers != 0) {
				if(writerId == curThreadId) {//test weather reader' ID is equal to current thread's ID.
					readers = 1;
					/**************************/
					ID_XOR = curThreadId;     //XOR
					ID_ADD.add(curThreadId);  //ADD
					/*************************/
					i_unlock();
					return;
				}
				i_unlock();//
				synchronized (c_read) {
					c_read.wait();
				}
				continue;
			}
			readers++;//update readers count
			/***************************/
			ID_XOR ^= curThreadId;
			ID_ADD.add(curThreadId);
			/**************************/
			i_unlock();
			return;
		}
	}
	public void ReadUnlock() {
		long curThreadId;
		
		curThreadId = Thread.currentThread().getId();
		i_lock();
		readers--;//update readers count
		/*****************************/
		ID_XOR ^= curThreadId;    //XOR
		ID_ADD.sub(curThreadId);  //SUB
		/*****************************/
		try {
			if(readers == 0) {
				synchronized (c_write){
					c_write.notify();
				}
			}
		}
		finally {
			i_unlock();
		}
	}
	public void WriteLock() throws InterruptedException {
		long curThreadId;
		
		curThreadId = Thread.currentThread().getId();
		while(true) {
			i_lock();
			if(writers != 0) {//Permit thread get writer-lock if it holding a writer-lock;   reentrant
				if(writerId == curThreadId) {
					writers++;
					i_unlock();
					return;
				}
			}
			else if(readers == 0) {//Permit thread get writer-lock if nobody holding reader-lock;
				writers = 1;
				writerId = curThreadId;
				i_unlock();
				return;
			}
			else {//Upgrade.
        //ID_XOR and ID_ADD to ensure only current thread holding reader-lock(s).
				/*
				if((ID_XOR == 0 || ID_XOR == CurThreadId) &&
					curThreadId * readers == ID_ADD){
						Permit thread get a writer-lock;
					}
				*/
				
				if(ID_XOR == 0 || ID_XOR == curThreadId) {
					UINT124 mul = new UINT124();
					mul.mul(curThreadId, readers);
					if(mul.cmp(ID_ADD) == 0) {
						writers = 1;
						writerId = curThreadId;
						i_unlock();
						return;
					}
				}
			}
			i_unlock();
			synchronized(c_write) {
				c_write.wait();
			}
		}
		
	}
	public void WriteUnlock() {
		i_lock();
		writers--;
		if(writers == 0) {
			if(readers == 0) {
				try {
					synchronized(c_write) {
						c_write.notify();
					}
					synchronized(c_read) {
						c_read.notifyAll();
					}
				}
				finally {
					i_unlock();
				}
				return;
			}
			else {
				try {
					synchronized(c_read) {
						c_read.notifyAll();
					}
				}
				finally {
					i_unlock();
				}
				return;
			}
		}
		i_unlock();
	}
}
