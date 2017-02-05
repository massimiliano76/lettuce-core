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

import java.lang.reflect.Method;
import java.util.List;

import com.lambdaworks.redis.AbstractRedisReactiveCommands;
import com.lambdaworks.redis.codec.RedisCodec;
import com.lambdaworks.redis.dynamic.codec.AnnotationRedisCodecResolver;
import com.lambdaworks.redis.dynamic.output.CodecAwareOutputFactoryResolver;
import com.lambdaworks.redis.dynamic.output.CommandOutputFactoryResolver;
import com.lambdaworks.redis.dynamic.segment.AnnotationCommandSegmentFactory;
import com.lambdaworks.redis.dynamic.segment.CommandSegments;
import com.lambdaworks.redis.internal.LettuceAssert;

/**
 * @author Mark Paluch
 * @since 5.0
 */
class ReactiveExecutableCommandLookupStrategy implements ExecutableCommandLookupStrategy {

    private final AbstractRedisReactiveCommands<Object, Object> redisReactiveCommands;
    private final ConversionService conversionService = new ConversionService();
    private final List<RedisCodec<?, ?>> redisCodecs;
    private final CommandOutputFactoryResolver outputFactoryResolver;
    private final ReactiveCommandFactoryResolver commandFactoryResolver;
    private final CommandMethodVerifier commandMethodVerifier;

    ReactiveExecutableCommandLookupStrategy(List<RedisCodec<?, ?>> redisCodecs,
            CommandOutputFactoryResolver outputFactoryResolver, CommandMethodVerifier commandMethodVerifier,
            AbstractRedisReactiveCommands<Object, Object> redisReactiveCommands) {

        this.redisReactiveCommands = redisReactiveCommands;
        this.redisCodecs = redisCodecs;
        this.outputFactoryResolver = outputFactoryResolver;
        this.commandMethodVerifier = commandMethodVerifier;

        ReactiveTypeAdapters.registerIn(this.conversionService);
        this.commandFactoryResolver = new ReactiveCommandFactoryResolver();
    }

    @Override
    public ExecutableCommand resolveCommandMethod(Method method, RedisCommandsMetadata commandsMetadata) {

        CommandMethod commandMethod = new CommandMethod(method);

        LettuceAssert.isTrue(commandMethod.isReactiveExecution(),
                String.format("Command method %s not supported by this command lookup strategy", method));

        ReactiveCommandSegmentCommandFactory commandFactory = commandFactoryResolver.resolveRedisCommandFactory(commandMethod,
                commandsMetadata);

        return new ConvertingCommand(conversionService, new ReactiveExecutableCommand(commandMethod, commandFactory,
                redisReactiveCommands));
    }

    class ReactiveCommandFactoryResolver implements CommandFactoryResolver {

        final AnnotationCommandSegmentFactory commandSegmentFactory = new AnnotationCommandSegmentFactory();
        final AnnotationRedisCodecResolver codecResolver;

        ReactiveCommandFactoryResolver() {
            codecResolver = new AnnotationRedisCodecResolver(redisCodecs);
        }

        public ReactiveCommandSegmentCommandFactory resolveRedisCommandFactory(CommandMethod commandMethod,
                RedisCommandsMetadata redisCommandsMetadata) {

            RedisCodec<?, ?> codec = codecResolver.resolve(commandMethod);

            if (codec == null) {
                throw new CommandCreationException(commandMethod, "Cannot resolve RedisCodec");
            }

            CommandSegments commandSegments = commandSegmentFactory.createCommandSegments(commandMethod);

            commandMethodVerifier.validate(commandSegments, commandMethod);

            CodecAwareOutputFactoryResolver outputFactoryResolver = new CodecAwareOutputFactoryResolver(
                    ReactiveExecutableCommandLookupStrategy.this.outputFactoryResolver, codec);

            return new ReactiveCommandSegmentCommandFactory(commandSegments, commandMethod, codec, outputFactoryResolver);
        }
    }
}
