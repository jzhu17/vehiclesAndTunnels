package cs131.pa2.Abstract;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.*;

import cs131.pa2.Abstract.Log.EventType;
import cs131.pa2.Abstract.Log.Log;
import cs131.pa2.CarsTunnels.Ambulance;

/**
 * A Vehicle is a Runnable which enters tunnels. You must subclass
 * Vehicle to customize its behavior (e.g., Car and Sled).
 *
 * When you start a thread which runs a Vehicle, the Vehicle will
 * immediately begin trying to enter the tunnel or tunnels passed into
 * its constructor by calling tryToEnter on each Tunnel instance. As
 * long as tryToEnter returns false (indicating that the Vehicle did
 * not enter that tunnel), the Vehicle will keep trying. This is
 * called busy-waiting.
 *
 * In addition to recreating the constructors, the only method that
 * you must override in Vehicle subclasses is getDefaultSpeed. This
 * instance method is called from the private init method, and the
 * integer that it returns is used as the speed for the vehicle.
 */
public abstract class Vehicle implements Runnable {
    private String            	name;
    private Direction          	direction;
    private Collection<Tunnel> 	tunnels;
    private int                	priority;
    private int                	speed;
    private Log 					log;
    
    private Lock 				lock; //lock for the vehicle 
    private Condition 			ambulance; //condition for all vehicles to check whether there is an ambulance

    /**
     * Initialize a Vehicle; called from Vehicle constructors.
     */
    private void init(String name, Direction direction, int priority, Log log) {
        this.name      = name;
        this.direction = direction;
        this.priority  = 0;
        this.speed     = getDefaultSpeed();
        this.log       = log;
        this.tunnels   = new ArrayList<Tunnel>();
        
        if(this.speed < 0 || this.speed > 9) {
            throw new RuntimeException("Vehicle has invalid speed");
        }
    }

    /**
     * Override in a subclass to determine the speed of the
     * vehicle.
     *
     * Must return a number between 0 and 9 (inclusive). Higher
     * numbers indicate greater speed. The faster a vehicle, the less
     * time it will spend in a tunnel.
     */
    protected abstract int getDefaultSpeed();

    /**
     * Create a Vehicle with default priority that can cross one of
     * a collection of tunnels.
     * 
     * @param name      The name of this vehicle to be displayed in the
     *                  output.
     * @param direction The side of the tunnel being entered.
     */
    public Vehicle(String name, Direction direction, Log log) {
        init(name, direction, 0, log);
    }
    
    public Vehicle(String name, Direction direction) {
        this(name, direction, Tunnel.DEFAULT_LOG);
    }
    
    /**
     * This method 'puts' the tunnel lock onto the vehicle inside of it
     * @param lock the specific vehicle's lock
     * @param ambulance 
     */
    public void setLock(Lock lock, Condition ambulance) {
    	this.lock = lock;
    	this.ambulance = ambulance;   
    }
    
    /**
     * Sets this vehicle's speed - used for preemptive priority scheduler test
     * @param speed the new speed to be set (0 to 9)
     */
    public void setSpeed(int speed) {
    	if(this.speed < 0 || this.speed > 9) {
            throw new RuntimeException("Invalid speed: "+ speed);
        }
    	this.speed = speed;
    }
    
    /** 
     * Sets this vehicle's priority - used for priority scheduling
     *
     * @param priority The new priority (between 0 and 4 inclusive)
     */
    public final void setPriority(int priority) {
        if(priority < 0 || priority > 4) {
            throw new RuntimeException("Invalid priority: " + priority);
        }
        this.priority = priority;
    }
    
    /**
     * Returns the priority of this vehicle
     *
     * @return This vehicle's priority.
     */
    public int getPriority() {
        return priority;
    }

    /**
     * Returns the name of this vehicle
     *
     * @return The name of this vehicle
     */
    public final String getName() {
        return name;
    }

    public String toString() {
    		return String.format("%s VEHICLE %s", this.direction, this.name);
    }
    
    public final void addTunnel(Tunnel newTunnel) {
        this.tunnels.add(newTunnel);
    }

    public final void addTunnel(Collection<Tunnel> newTunnels) {
        this.tunnels.addAll(newTunnels);
    }
    
    /**
     * Find and cross through one of the tunnels.
     * 
     * When a thread is run, it keeps looping through its collection
     * of available tunnels until it succeeds in entering one of
     * them. Then, it will call doWhileInTunnel (to simulate doing
     * some work inside the tunnel, i.e., that it takes time to cross
     * the tunnel), then exit that tunnel.
     */
    public final void run() {
        while(true) {
            for(Tunnel tunnel : tunnels) {
                if(tunnel.tryToEnter(this)) {
                    doWhileInTunnel();
                    tunnel.exitTunnel(this);
                    this.log.addToLog(this, EventType.COMPLETE);
                    return; 
                }
            }
        }
    }
    
    /**
     * Returns the direction of this vehicle
     *
     * @return the direction of this vehicle
     */
    public final Direction getDirection() {
        return direction;
    }

  
    /**
     * This is what your vehicle does while inside the tunnel to
     * simulate taking time to "cross" the tunnel. The faster your
     * vehicle is, the less time this will take.
     */
    public final void doWhileInTunnel() {
    	//if lock is null, all threads sleep because it's simple priority scheduler
        if(this.lock == null) {
        	try {
				Thread.sleep((10 - speed) * 100);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
        } else { //PreemptivePriorityScheduler
        	this.lock.lock();
        	try {
        		//if is ambulance, interrupt, sleep, and then signal other vehicles when exit out of tunnel
        		if (this instanceof Ambulance) {
        			this.ambulance.signalAll();
        			Thread.sleep((10 - speed) * 100);
        			this.ambulance.signalAll();
        		} else {
        			//for normal vehicles, await for a certain period of time 
        			long nanos = this.ambulance.awaitNanos((10 - speed) * 100 * 1000000); 
        			//only exits when remaining time is 0
        			while(nanos > 0) {
        				this.ambulance.await();
        				nanos = this.ambulance.awaitNanos(nanos); 
        			}
        		}
            } catch(InterruptedException e) {
            	System.err.println("Interrupted vehicle " + getName());
            } finally {
            	this.lock.unlock();
            }
        }
        
    }
    
    @Override
    public int hashCode() {
        int hash = 7;
        hash = 23 * hash + Objects.hashCode(this.name);
        hash = 23 * hash + Objects.hashCode(this.direction);
        hash = 23 * hash + this.speed;
        hash = 23 * hash + this.priority;
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Vehicle other = (Vehicle) obj;
        if (!Objects.equals(this.name, other.name)) {
            return false;
        }
        if (this.direction != other.direction) {
            return false;
        }
        if (this.speed != other.speed) {
            return false;
        }
        if (this.priority != other.priority) {
            return false;
        }
        return true;
    }
}
