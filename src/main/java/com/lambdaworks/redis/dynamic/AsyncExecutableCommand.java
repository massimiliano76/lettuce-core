/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.lambdaworks.redis.dynamic;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import com.lambdaworks.redis.LettuceFutures;
import com.lambdaworks.redis.api.StatefulConnection;
import com.lambdaworks.redis.dynamic.domain.Timeout;
import com.lambdaworks.redis.dynamic.parameter.ExecutionSpecificParameters;
import com.lambdaworks.redis.protocol.AsyncCommand;
import com.lambdaworks.redis.protocol.RedisCommand;

/**
 * An {@link ExecutableCommand} that is executed asynchronously or synchronously.
 * 
 * @author Mark Paluch
 * @since 5.0
 */
class AsyncExecutableCommand implements ExecutableCommand {

    private final CommandMethod commandMethod;
    private final CommandFactory commandFactory;
    private final StatefulConnection<Object, Object> connection;

    AsyncExecutableCommand(CommandMethod commandMethod, CommandFactory commandFactory,
            StatefulConnection<Object, Object> connection) {

        this.commandMethod = commandMethod;
        this.commandFactory = commandFactory;
        this.connection = connection;
    }

    @Override
    public Object execute(Object[] parameters) throws ExecutionException, InterruptedException {

        RedisCommand<Object, Object, Object> command = commandFactory.createCommand(parameters);

        return dispatchCommand(parameters, command);
    }

    protected Object dispatchCommand(Object[] arguments, RedisCommand<Object, Object, Object> command)
            throws InterruptedException, java.util.concurrent.ExecutionException {

        AsyncCommand<Object, Object, Object> asyncCommand = new AsyncCommand<>(command);

        if (commandMethod.isFutureExecution()) {
            return connection.dispatch(asyncCommand);
        }

        connection.dispatch(asyncCommand);

        long timeout = connection.getTimeout();
        TimeUnit unit = connection.getTimeoutUnit();

        if (commandMethod.getParameters() instanceof ExecutionSpecificParameters) {
            ExecutionSpecificParameters executionSpecificParameters = (ExecutionSpecificParameters) commandMethod
                    .getParameters();

            if (executionSpecificParameters.hasTimeoutIndex()) {
                Timeout timeoutArg = (Timeout) arguments[executionSpecificParameters.getTimeoutIndex()];
                if (timeoutArg != null) {
                    timeout = timeoutArg.getTimeout();
                    unit = timeoutArg.getTimeUnit();
                }
            }
        }

        LettuceFutures.awaitAll(timeout, unit, asyncCommand);

        return asyncCommand.get();
    }

    @Override
    public CommandMethod getCommandMethod() {
        return commandMethod;
    }
}
