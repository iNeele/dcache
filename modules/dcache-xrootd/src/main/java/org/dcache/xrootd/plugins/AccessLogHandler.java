/* dCache - http://www.dcache.org/
 *
 * Copyright (C) 2014 Deutsches Elektronen-Synchrotron
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.dcache.xrootd.plugins;

import static com.google.common.base.Strings.emptyToNull;
import static org.dcache.util.NetLoggerBuilder.Level.DEBUG;
import static org.dcache.util.NetLoggerBuilder.Level.ERROR;
import static org.dcache.util.NetLoggerBuilder.Level.INFO;
import static org.dcache.xrootd.protocol.XrootdProtocol.kXR_ArgInvalid;
import static org.dcache.xrootd.protocol.XrootdProtocol.kXR_ArgMissing;
import static org.dcache.xrootd.protocol.XrootdProtocol.kXR_ArgTooLong;
import static org.dcache.xrootd.protocol.XrootdProtocol.kXR_AttrNotFound;
import static org.dcache.xrootd.protocol.XrootdProtocol.kXR_AuthFailed;
import static org.dcache.xrootd.protocol.XrootdProtocol.kXR_BadPayload;
import static org.dcache.xrootd.protocol.XrootdProtocol.kXR_Cancelled;
import static org.dcache.xrootd.protocol.XrootdProtocol.kXR_ChkSumErr;
import static org.dcache.xrootd.protocol.XrootdProtocol.kXR_Conflict;
import static org.dcache.xrootd.protocol.XrootdProtocol.kXR_DecryptErr;
import static org.dcache.xrootd.protocol.XrootdProtocol.kXR_FSError;
import static org.dcache.xrootd.protocol.XrootdProtocol.kXR_FileLocked;
import static org.dcache.xrootd.protocol.XrootdProtocol.kXR_FileNotOpen;
import static org.dcache.xrootd.protocol.XrootdProtocol.kXR_IOError;
import static org.dcache.xrootd.protocol.XrootdProtocol.kXR_Impossible;
import static org.dcache.xrootd.protocol.XrootdProtocol.kXR_InvalidRequest;
import static org.dcache.xrootd.protocol.XrootdProtocol.kXR_ItExists;
import static org.dcache.xrootd.protocol.XrootdProtocol.kXR_NoMemory;
import static org.dcache.xrootd.protocol.XrootdProtocol.kXR_NoSpace;
import static org.dcache.xrootd.protocol.XrootdProtocol.kXR_NotAuthorized;
import static org.dcache.xrootd.protocol.XrootdProtocol.kXR_NotFile;
import static org.dcache.xrootd.protocol.XrootdProtocol.kXR_NotFound;
import static org.dcache.xrootd.protocol.XrootdProtocol.kXR_Overloaded;
import static org.dcache.xrootd.protocol.XrootdProtocol.kXR_QPrep;
import static org.dcache.xrootd.protocol.XrootdProtocol.kXR_QStats;
import static org.dcache.xrootd.protocol.XrootdProtocol.kXR_Qckscan;
import static org.dcache.xrootd.protocol.XrootdProtocol.kXR_Qcksum;
import static org.dcache.xrootd.protocol.XrootdProtocol.kXR_Qconfig;
import static org.dcache.xrootd.protocol.XrootdProtocol.kXR_Qopaque;
import static org.dcache.xrootd.protocol.XrootdProtocol.kXR_Qopaquf;
import static org.dcache.xrootd.protocol.XrootdProtocol.kXR_Qopaqug;
import static org.dcache.xrootd.protocol.XrootdProtocol.kXR_Qspace;
import static org.dcache.xrootd.protocol.XrootdProtocol.kXR_Qvisa;
import static org.dcache.xrootd.protocol.XrootdProtocol.kXR_Qxattr;
import static org.dcache.xrootd.protocol.XrootdProtocol.kXR_ServerError;
import static org.dcache.xrootd.protocol.XrootdProtocol.kXR_SigVerErr;
import static org.dcache.xrootd.protocol.XrootdProtocol.kXR_TLSRequired;
import static org.dcache.xrootd.protocol.XrootdProtocol.kXR_Unsupported;
import static org.dcache.xrootd.protocol.XrootdProtocol.kXR_auth;
import static org.dcache.xrootd.protocol.XrootdProtocol.kXR_bind;
import static org.dcache.xrootd.protocol.XrootdProtocol.kXR_chkpoint;
import static org.dcache.xrootd.protocol.XrootdProtocol.kXR_chmod;
import static org.dcache.xrootd.protocol.XrootdProtocol.kXR_close;
import static org.dcache.xrootd.protocol.XrootdProtocol.kXR_dirlist;
import static org.dcache.xrootd.protocol.XrootdProtocol.kXR_endsess;
import static org.dcache.xrootd.protocol.XrootdProtocol.kXR_fattr;
import static org.dcache.xrootd.protocol.XrootdProtocol.kXR_fsReadOnly;
import static org.dcache.xrootd.protocol.XrootdProtocol.kXR_gpfile;
import static org.dcache.xrootd.protocol.XrootdProtocol.kXR_handshake;
import static org.dcache.xrootd.protocol.XrootdProtocol.kXR_inProgress;
import static org.dcache.xrootd.protocol.XrootdProtocol.kXR_isDirectory;
import static org.dcache.xrootd.protocol.XrootdProtocol.kXR_locate;
import static org.dcache.xrootd.protocol.XrootdProtocol.kXR_login;
import static org.dcache.xrootd.protocol.XrootdProtocol.kXR_mkdir;
import static org.dcache.xrootd.protocol.XrootdProtocol.kXR_mv;
import static org.dcache.xrootd.protocol.XrootdProtocol.kXR_noErrorYet;
import static org.dcache.xrootd.protocol.XrootdProtocol.kXR_noReplicas;
import static org.dcache.xrootd.protocol.XrootdProtocol.kXR_noserver;
import static org.dcache.xrootd.protocol.XrootdProtocol.kXR_open;
import static org.dcache.xrootd.protocol.XrootdProtocol.kXR_overQuota;
import static org.dcache.xrootd.protocol.XrootdProtocol.kXR_pgread;
import static org.dcache.xrootd.protocol.XrootdProtocol.kXR_pgwrite;
import static org.dcache.xrootd.protocol.XrootdProtocol.kXR_ping;
import static org.dcache.xrootd.protocol.XrootdProtocol.kXR_prepare;
import static org.dcache.xrootd.protocol.XrootdProtocol.kXR_protocol;
import static org.dcache.xrootd.protocol.XrootdProtocol.kXR_query;
import static org.dcache.xrootd.protocol.XrootdProtocol.kXR_read;
import static org.dcache.xrootd.protocol.XrootdProtocol.kXR_readv;
import static org.dcache.xrootd.protocol.XrootdProtocol.kXR_rm;
import static org.dcache.xrootd.protocol.XrootdProtocol.kXR_rmdir;
import static org.dcache.xrootd.protocol.XrootdProtocol.kXR_set;
import static org.dcache.xrootd.protocol.XrootdProtocol.kXR_sigver;
import static org.dcache.xrootd.protocol.XrootdProtocol.kXR_stat;
import static org.dcache.xrootd.protocol.XrootdProtocol.kXR_statx;
import static org.dcache.xrootd.protocol.XrootdProtocol.kXR_sync;
import static org.dcache.xrootd.protocol.XrootdProtocol.kXR_truncate;
import static org.dcache.xrootd.protocol.XrootdProtocol.kXR_write;
import static org.dcache.xrootd.protocol.XrootdProtocol.kXR_writev;

import com.google.common.base.Strings;
import dmg.cells.nucleus.CDC;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import java.net.InetSocketAddress;
import java.time.Instant;
import javax.security.auth.Subject;
import org.dcache.auth.LoginReply;
import org.dcache.auth.Subjects;
import org.dcache.util.NetLoggerBuilder;
import org.dcache.xrootd.door.LoginEvent;
import org.dcache.xrootd.protocol.XrootdProtocol;
import org.dcache.xrootd.protocol.messages.CloseRequest;
import org.dcache.xrootd.protocol.messages.EndSessionRequest;
import org.dcache.xrootd.protocol.messages.ErrorResponse;
import org.dcache.xrootd.protocol.messages.LocateRequest;
import org.dcache.xrootd.protocol.messages.LoginRequest;
import org.dcache.xrootd.protocol.messages.LoginResponse;
import org.dcache.xrootd.protocol.messages.MkDirRequest;
import org.dcache.xrootd.protocol.messages.MvRequest;
import org.dcache.xrootd.protocol.messages.OpenRequest;
import org.dcache.xrootd.protocol.messages.OpenResponse;
import org.dcache.xrootd.protocol.messages.PathRequest;
import org.dcache.xrootd.protocol.messages.PrepareRequest;
import org.dcache.xrootd.protocol.messages.QueryRequest;
import org.dcache.xrootd.protocol.messages.ReadRequest;
import org.dcache.xrootd.protocol.messages.ReadVRequest;
import org.dcache.xrootd.protocol.messages.RedirectResponse;
import org.dcache.xrootd.protocol.messages.SetRequest;
import org.dcache.xrootd.protocol.messages.StatRequest;
import org.dcache.xrootd.protocol.messages.StatRequest.Target;
import org.dcache.xrootd.protocol.messages.StatResponse;
import org.dcache.xrootd.protocol.messages.StatxRequest;
import org.dcache.xrootd.protocol.messages.SyncRequest;
import org.dcache.xrootd.protocol.messages.WriteRequest;
import org.dcache.xrootd.protocol.messages.XrootdRequest;
import org.dcache.xrootd.protocol.messages.XrootdResponse;
import org.dcache.xrootd.util.FileStatus;
import org.slf4j.Logger;

@ChannelHandler.Sharable
public class AccessLogHandler extends ChannelDuplexHandler {

    protected final Logger logger;

    public AccessLogHandler(Logger logger) {
        this.logger = logger;
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof LoginEvent) {
            LoginReply loginReply = ((LoginEvent) evt).getLoginReply();
            Subject subject = loginReply.getSubject();
            NetLoggerBuilder log = new NetLoggerBuilder(INFO,
                  "org.dcache.xrootd.login").omitNullValues();
            log.add("session", CDC.getSession());
            log.add("user.dn", Subjects.getDn(subject));
            log.add("user.mapped", subject);
            log.toLogger(logger);
        }
        ctx.fireUserEventTriggered(evt);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        NetLoggerBuilder log = new NetLoggerBuilder(INFO,
              "org.dcache.xrootd.connection.start").omitNullValues();
        log.add("session", CDC.getSession());
        log.add("socket.remote", (InetSocketAddress) ctx.channel().remoteAddress());
        log.add("socket.local", (InetSocketAddress) ctx.channel().localAddress());
        log.toLogger(logger);
        ctx.fireChannelActive();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        NetLoggerBuilder log = new NetLoggerBuilder(INFO,
              "org.dcache.xrootd.connection.end").omitNullValues();
        log.add("session", CDC.getSession());
        log.toLogger(logger);
        ctx.fireChannelInactive();
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise)
          throws Exception {
        if (msg instanceof XrootdResponse<?> && logger.isErrorEnabled()) {
            XrootdResponse<?> response = (XrootdResponse<?>) msg;
            XrootdRequest request = response.getRequest();

            NetLoggerBuilder.Level level;
            if (response instanceof ErrorResponse) {
                level = ERROR;
            } else if (request instanceof WriteRequest || request instanceof ReadRequest
                  || request instanceof ReadVRequest) {
                level = DEBUG;
            } else {
                level = INFO;
            }

            if (level == ERROR || level == INFO && logger.isInfoEnabled()
                  || level == DEBUG && logger.isDebugEnabled()) {
                NetLoggerBuilder log = new NetLoggerBuilder(level,
                      "org.dcache.xrootd.request").omitNullValues();
                log.add("session", CDC.getSession());
                log.add("request", getRequestId(request));

                if (request instanceof PathRequest) {
                    log.add("path", (Strings.emptyToNull(((PathRequest) request).getPath())));
                    log.add("opaque", (Strings.emptyToNull(((PathRequest) request).getOpaque())));
                    if (request instanceof OpenRequest) {
                        if (!((OpenRequest) request).isReadOnly()) {
                            int mode = ((OpenRequest) request).getUMask();
                            if (mode == 0) {
                                log.add("mode", "0");
                            } else {
                                log.add("mode", "0" + Integer.toOctalString(mode));
                            }
                        }
                        log.add("options",
                              "0x" + Integer.toHexString(((OpenRequest) request).getOptions()));
                    } else if (request instanceof LocateRequest) {
                        log.add("options",
                              "0x" + Integer.toHexString(((LocateRequest) request).getOptions()));
                    } else if (request instanceof MkDirRequest) {
                        log.add("options",
                              "0x" + Integer.toHexString(((MkDirRequest) request).getOptions()));
                    } else if (request instanceof StatRequest) {
                        if (((StatRequest) request).getTarget() == Target.FHANDLE) {
                            log.add("handle", ((StatRequest) request).getFhandle());
                        }
                        log.add("vfs", ((StatRequest) request).isVfsSet());
                    }
                } else if (request instanceof CloseRequest) {
                    log.add("handle", ((CloseRequest) request).getFileHandle());
                } else if (request instanceof LoginRequest) {
                    log.add("username", ((LoginRequest) request).getUserName());
                    log.add("capver", ((LoginRequest) request).getClientProtocolVersion());
                    log.add("pid", ((LoginRequest) request).getPID());
                    log.add("token", emptyToNull(((LoginRequest) request).getToken()));
                } else if (request instanceof MvRequest) {
                    log.add("source", ((MvRequest) request).getSourcePath());
                    log.add("target", ((MvRequest) request).getTargetPath());
                } else if (request instanceof PrepareRequest) {
                    log.add("options",
                          "0x" + Integer.toHexString(((PrepareRequest) request).getOptions()));
                    if (((PrepareRequest) request).getPathList().length == 1) {
                        log.add("path", ((PrepareRequest) request).getPathList()[0]);
                    } else {
                        log.add("files", ((PrepareRequest) request).getPathList().length);
                    }
                } else if (request instanceof QueryRequest) {
                    log.add("reqcode", getQueryReqCode(request));
                    int fhandle = ((QueryRequest) request).getFhandle();
                    if (fhandle != 0) {
                        log.add("fhandle", fhandle);
                    }
                    log.add("args", Strings.emptyToNull(((QueryRequest) request).getArgs()));
                } else if (request instanceof StatxRequest) {
                    if (((StatxRequest) request).getPaths().length == 1) {
                        log.add("path", ((StatxRequest) request).getPaths()[0]);
                    } else {
                        log.add("files", ((StatxRequest) request).getPaths().length);
                    }
                } else if (request instanceof SetRequest) {
                    final String APPID_PREFIX = "appid ";
                    final int APPID_PREFIX_LENGTH = APPID_PREFIX.length();
                    final int APPID_MSG_LENGTH = 80;
                    String data = ((SetRequest) request).getData();
                    if (data.startsWith(APPID_PREFIX)) {
                        log.add("appid", data.substring(APPID_PREFIX_LENGTH,
                              Math.min(APPID_PREFIX_LENGTH + APPID_MSG_LENGTH,
                                    data.length())));
                    }
                } else if (request instanceof EndSessionRequest) {
                    log.add("sessionId", ((EndSessionRequest) request).getSessionId());
                } else if (request instanceof SyncRequest) {
                    log.add("handle", ((SyncRequest) request).getFileHandle());
                }

                log.add("response", getStatusCode(response));
                if (response instanceof ErrorResponse) {
                    log.add("error.code", getErrorCode((ErrorResponse) response));
                    log.add("error.msg", ((ErrorResponse) response).getErrorMessage());
                } else if (response instanceof RedirectResponse) {
                    log.add("host", ((RedirectResponse) response).getHost());
                    log.add("port", ((RedirectResponse) response).getPort());
                    log.add("token", emptyToNull(((RedirectResponse) response).getToken()));
                } else if (response instanceof StatResponse) {
                    log.add("flags", ((StatResponse) response).getFlags());
                    log.add("modtime",
                          Instant.ofEpochSecond(((StatResponse) response).getModificationTime()));
                    log.add("size", ((StatResponse) response).getSize());
                } else if (response instanceof LoginResponse) {
                    log.add("sessionId", ((LoginResponse) response).getSessionId());
                    log.add("sec", emptyToNull(((LoginResponse) response).getSec()));
                } else if (response instanceof OpenResponse) {
                    log.add("handle", ((OpenResponse) response).getFileHandle());
                    FileStatus fs = ((OpenResponse) response).getFileStatus();
                    if (fs != null) {
                        log.add("flags", fs.getFlags());
                        log.add("modtime", Instant.ofEpochSecond(fs.getModificationTime()));
                        log.add("size", fs.getSize());
                    }
                }

                log.toLogger(logger);
            }
        }
        ctx.write(msg, promise);
    }

    private String getErrorCode(ErrorResponse response) {
        int errorNumber = response.getErrorNumber();
        switch (errorNumber) {
            case kXR_ArgInvalid:
                return "ArgInvalid";
            case kXR_ArgMissing:
                return "ArgMissing";
            case kXR_ArgTooLong:
                return "ArgTooLong";
            case kXR_FileLocked:
                return "FileLocked";
            case kXR_FileNotOpen:
                return "FileNotOpen";
            case kXR_FSError:
                return "FSError";
            case kXR_InvalidRequest:
                return "InvalidRequest";
            case kXR_IOError:
                return "IOError";
            case kXR_NoMemory:
                return "NoMemory";
            case kXR_NoSpace:
                return "NoSpace";
            case kXR_NotAuthorized:
                return "NotAuhorized";
            case kXR_NotFound:
                return "NotFound";
            case kXR_ServerError:
                return "ServerError";
            case kXR_Unsupported:
                return "Unsupported";
            case kXR_noserver:
                return "noserver";
            case kXR_NotFile:
                return "NotFile";
            case kXR_isDirectory:
                return "isDirectory";
            case kXR_Cancelled:
                return "Cancelled";
            case kXR_ItExists:
                return "ItExists";
            case kXR_ChkSumErr:
                return "ChkSumErr";
            case kXR_inProgress:
                return "inProgress";
            case kXR_overQuota:
                return "overQuota";
            case kXR_SigVerErr:
                return "SigVerErr";
            case kXR_DecryptErr:
                return "DecryptErr";
            case kXR_Overloaded:
                return "Overloaded";
            case kXR_fsReadOnly:
                return "fsReadOnly";
            case kXR_BadPayload:
                return "BadPayload";
            case kXR_AttrNotFound:
                return "AttrNotFound";
            case kXR_TLSRequired:
                return "TLSRequired";
            case kXR_noReplicas:
                return "noReplicas";
            case kXR_AuthFailed:
                return "AuthFailed";
            case kXR_Impossible:
                return "Impossible";
            case kXR_Conflict:
                return "Conflict";
            case kXR_noErrorYet:
                return "noErrorYet";
            default:
                return String.valueOf(errorNumber);
        }
    }

    private static String getStatusCode(XrootdResponse<?> response) {
        int status = response.getStatus();
        switch (status) {
            case XrootdProtocol.kXR_authmore:
                return "authmore";
            case XrootdProtocol.kXR_error:
                return "error";
            case XrootdProtocol.kXR_ok:
                return "ok";
            case XrootdProtocol.kXR_oksofar:
                return "oksofar";
            case XrootdProtocol.kXR_redirect:
                return "redirect";
            case XrootdProtocol.kXR_wait:
                return "wait";
            case XrootdProtocol.kXR_waitresp:
                return "waitresp";
            default:
                return String.valueOf(status);
        }
    }

    private static String getQueryReqCode(XrootdRequest request) {
        int reqcode = ((QueryRequest) request).getReqcode();
        switch (reqcode) {
            case kXR_QStats:
                return "QStats";
            case kXR_QPrep:
                return "QPrep";
            case kXR_Qcksum:
                return "QCksum";
            case kXR_Qxattr:
                return "QXattr";
            case kXR_Qspace:
                return "QSpace";
            case kXR_Qckscan:
                return "QCksCan";
            case kXR_Qconfig:
                return "QConfig";
            case kXR_Qvisa:
                return "QVisa";
            case kXR_Qopaque:
                return "QOpaque";
            case kXR_Qopaquf:
                return "QOpaquf";
            case kXR_Qopaqug:
                return "QOpaqug";
            default:
                return String.valueOf(reqcode);
        }
    }

    private static String getRequestId(XrootdRequest request) {
        switch (request.getRequestId()) {
            case kXR_handshake:
                return "handshake";
            case kXR_auth:
                return "auth";
            case kXR_query:
                return "query";
            case kXR_chmod:
                return "chdmod";
            case kXR_close:
                return "close";
            case kXR_dirlist:
                return "dirlist";
            case kXR_gpfile:
                return "gpfile";
            case kXR_protocol:
                return "protocol";
            case kXR_login:
                return "login";
            case kXR_mkdir:
                return "mkdir";
            case kXR_mv:
                return "mv";
            case kXR_open:
                return "open";
            case kXR_ping:
                return "ping";
            case kXR_chkpoint:
                return "chkpoint";
            case kXR_read:
                return "read";
            case kXR_rm:
                return "rm";
            case kXR_rmdir:
                return "rmdir";
            case kXR_sync:
                return "sync";
            case kXR_stat:
                return "stat";
            case kXR_set:
                return "set";
            case kXR_write:
                return "write";
            case kXR_fattr:
                return "fattr";
            case kXR_prepare:
                return "prepare";
            case kXR_statx:
                return "statx";
            case kXR_endsess:
                return "endsess";
            case kXR_bind:
                return "bind";
            case kXR_readv:
                return "readv";
            case kXR_pgwrite:
                return "pgwrite";
            case kXR_locate:
                return "locate";
            case kXR_truncate:
                return "truncate";
            case kXR_sigver:
                return "sigver";
            case kXR_pgread:
                return "pgread";
            case kXR_writev:
                return "writev";
            default:
                return String.valueOf(request.getRequestId());
        }
    }
}
