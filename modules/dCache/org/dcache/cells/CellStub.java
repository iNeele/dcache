package org.dcache.cells;

import dmg.cells.nucleus.CellEndpoint;
import dmg.cells.nucleus.CellMessage;
import dmg.cells.nucleus.CellPath;
import dmg.cells.nucleus.CellMessageAnswerable;
import dmg.cells.nucleus.NoRouteToCellException;

import diskCacheV111.vehicles.Message;
import diskCacheV111.util.CacheException;
import org.dcache.util.CacheExceptionFactory;

/**
 * Stub class for common cell communication patterns. An instance
 * of the template class encapsulates properties such as the
 * destination and timeout for communication.
 *
 * Operations are aware of the dCache Message class and the
 * CacheException class, and are able to interpret dCache error
 * messages.
 */
public class CellStub
    implements CellMessageSender
{
    private CellEndpoint _endpoint;
    private CellPath _destination;
    private long _timeout = 30000;

    public CellStub()
    {
    }

    @Override
    public void setCellEndpoint(CellEndpoint endpoint)
    {
        _endpoint = endpoint;
    }

    public void setDestination(String destination)
    {
        setDestinationPath(new CellPath(destination));
    }

    public void setDestinationPath(CellPath destination)
    {
        _destination = destination;
    }

    public CellPath getDestinationPath()
    {
        return _destination;
    }

    /**
     * Sets the communication timeout of the stub.
     * @param timeout the timeout in milliseconds
     */
    public void setTimeout(long timeout)
    {
        _timeout = timeout;
    }

    /**
     * Returns the communication timeout of the stub.
     */
    public long getTimeout()
    {
        return _timeout;
    }

    /**
     * Sends a message and waits for the reply. The reply is expected
     * to contain a message object of the same type as the message
     * object that was sent, and the return code of that message is
     * expected to be zero. If either is not the case, an exception is
     * thrown.
     *
     * @param  msg     the message object to send
     * @return         the message object from the reply
     * @throws InterruptedException If the thread is interrupted
     * @throws CacheException If the message could not be sent, a
     *       timeout occured, the object in the reply was of the wrong
     *       type, or the return code was non-zero.
     */
    public <T extends Message> T sendAndWait(T msg)
        throws CacheException, InterruptedException
    {
        return sendAndWait(_destination, msg);
    }

    /**
     * Sends a message and waits for the reply. The reply is expected
     * to contain a message object of the same type as the message
     * object that was sent, and the return code of that message is
     * expected to be zero. If either is not the case, an exception is
     * thrown.
     *
     * @param  path    the destination cell
     * @param  msg     the message object to send
     * @return         the message object from the reply
     * @throws InterruptedException If the thread is interrupted
     * @throws CacheException If the message could not be sent, a
     *       timeout occured, the object in the reply was of the wrong
     *       type, or the return code was non-zero.
     */
    public <T extends Message> T sendAndWait(CellPath path, T msg)
        throws CacheException, InterruptedException
    {
        msg.setReplyRequired(true);

        CellMessage replyMessage;
        try {
            replyMessage =
                _endpoint.sendAndWait(new CellMessage(path, msg), _timeout);
        } catch (NoRouteToCellException e) {
            /* From callers point of view a timeout due to a lost
             * message or a missing route to the destination is pretty
             * much the same, so we report this as a timeout. The
             * error message gives the details.
             */
            throw new CacheException(CacheException.TIMEOUT, e.getMessage());
        }

        if (replyMessage == null) {
            String errmsg = String.format("Request to %s timed out.", path);
            throw new CacheException(CacheException.TIMEOUT, errmsg);
        }

        Object replyObject = replyMessage.getMessageObject();
        if (!(msg.getClass().isInstance(replyObject))) {
            String errmsg = "Got unexpected message of class " +
                replyObject.getClass() + " from " +
                replyMessage.getSourceAddress();
            throw new CacheException(CacheException.UNEXPECTED_SYSTEM_EXCEPTION,
                                     errmsg);
        }

        T reply = (T)replyObject;
        if (reply.getReturnCode() != 0) {
            throw CacheExceptionFactory.exceptionOf(reply);
        }

        return reply;
    }

    /**
     * Sends <code>message</code> asynchronously, expecting a result
     * of type <code>type</code>. The result is delivered to
     * <code>callback</code>.
     */
    public <T> void send(Object message, Class<T> type,
                         MessageCallback<T> callback)
    {
        if (_destination == null)
            throw new IllegalStateException("Destination must be specified");
        send(_destination, message, type, callback);
    }

    /**
     * Sends <code>message</code> asynchronously to
     * <code>destination</code>, expecting a result of type
     * <code>type</code>. The result is delivered to
     * <code>callback</code>.
     */
    public <T> void send(CellPath destination,
                         Object message, Class<T> type,
                         MessageCallback<T> callback)
    {
        if (message instanceof Message) {
            ((Message) message).setReplyRequired(true);
        }
        _endpoint.sendMessage(new CellMessage(destination, message),
                              new CellCallback<T>(type, callback),
                              _timeout);
    }

    /**
     * Sends <code>message</code> to <code>destination</code>.
     */
    public void send(Object message) {
        if (_destination == null) {
            throw new IllegalStateException("Destination must be specified");
        }
        send(_destination, message);
    }


    /**
     * Sends <code>message</code> to <code>destination</code>.
     */
    public void send(CellPath destination, Object message) {
        try {
            _endpoint.sendMessage(new CellMessage(destination, message));
        } catch (NoRouteToCellException e) {
            /*
             * caller have to be prepared that message can be lost.
             */
        }
    }

    /**
     * Adapter class to wrap MessageCallback in CellMessageAnswerable.
     */
    static class CellCallback<T> implements CellMessageAnswerable
    {
        private final MessageCallback<T> _callback;
        private final Class<T> _type;

        CellCallback(Class<T> type, MessageCallback<T> callback)
        {
            _callback = callback;
            _type = type;
        }

        @Override
        public void answerArrived(CellMessage request, CellMessage answer)
        {
            Object o = answer.getMessageObject();
            if (_type.isInstance(o)) {
                if (o instanceof Message) {
                    Message msg = (Message) o;
                    int rc = msg.getReturnCode();
                    if (rc == 0) {
                        _callback.success(_type.cast(o));
                    } else {
                        _callback.failure(rc, msg.getErrorObject());
                    }
                } else {
                    _callback.success(_type.cast(o));
                }
            } else if (o instanceof Exception) {
                exceptionArrived(request, (Exception) o);
            } else {
                _callback.failure(CacheException.UNEXPECTED_SYSTEM_EXCEPTION,
                                  "Unexpected reply: " + o);
            }
        }

        @Override
        public void answerTimedOut(CellMessage request)
        {
            _callback.timeout();
        }

        @Override
        public void exceptionArrived(CellMessage request, Exception exception)
        {
            if (exception instanceof NoRouteToCellException) {
                _callback.noroute();
            } else if (exception instanceof CacheException) {
                CacheException e = (CacheException) exception;
                _callback.failure(e.getRc(),
                        CacheExceptionFactory.exceptionOf(e.getRc(), e.getMessage()));
            } else {
                _callback.failure(CacheException.UNEXPECTED_SYSTEM_EXCEPTION,
                                  exception.toString());
            }
        }
    }
}