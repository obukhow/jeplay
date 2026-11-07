var __defProp = Object.defineProperty;
var __export = (target, all) => {
  for (var name in all)
    __defProp(target, name, { get: all[name], enumerable: true });
};

// node_modules/@yume-chan/async/esm/promise-resolver.js
var PromiseResolver = class {
  #promise;
  get promise() {
    return this.#promise;
  }
  #resolve;
  #reject;
  #state = "running";
  get state() {
    return this.#state;
  }
  constructor() {
    this.#promise = new Promise((resolve, reject) => {
      this.#resolve = resolve;
      this.#reject = reject;
    });
  }
  resolve = (value) => {
    this.#resolve(value);
    this.#state = "resolved";
  };
  reject = (reason) => {
    this.#reject(reason);
    this.#state = "rejected";
  };
};

// node_modules/@yume-chan/async/esm/async-operation-manager.js
var AsyncOperationManager = class {
  nextId;
  pendingResolvers = /* @__PURE__ */ new Map();
  constructor(startId = 0) {
    this.nextId = startId;
  }
  add() {
    const id = this.nextId++;
    const resolver = new PromiseResolver();
    this.pendingResolvers.set(id, resolver);
    return [id, resolver.promise];
  }
  getResolver(id) {
    if (!this.pendingResolvers.has(id)) {
      return null;
    }
    const resolver = this.pendingResolvers.get(id);
    this.pendingResolvers.delete(id);
    return resolver;
  }
  resolve(id, result) {
    const resolver = this.getResolver(id);
    if (resolver !== null) {
      resolver.resolve(result);
      return true;
    }
    return false;
  }
  reject(id, reason) {
    const resolver = this.getResolver(id);
    if (resolver !== null) {
      resolver.reject(reason);
      return true;
    }
    return false;
  }
};

// node_modules/@yume-chan/async/esm/delay.js
function delay(time) {
  return new Promise((resolve) => {
    globalThis.setTimeout(() => resolve(), time);
  });
}

// node_modules/@yume-chan/async/esm/maybe-promise.js
function isPromiseLike(value) {
  return typeof value === "object" && value !== null && "then" in value;
}

// node_modules/@yume-chan/struct/esm/bipedal.js
function advance(iterator, next) {
  while (true) {
    const { done, value } = iterator.next(next);
    if (done) {
      return value;
    }
    if (isPromiseLike(value)) {
      return value.then((value2) => advance(iterator, { resolved: value2 }), (error) => advance(iterator, { error }));
    }
    next = value;
  }
}
function bipedal(fn) {
  return function(...args) {
    const iterator = fn.call(this, function* (value) {
      if (isPromiseLike(value)) {
        const result = yield value;
        if ("resolved" in result) {
          return result.resolved;
        } else {
          throw result.error;
        }
      }
      return value;
    }, ...args);
    return advance(iterator, void 0);
  };
}

// node_modules/@yume-chan/struct/esm/buffer.js
var EmptyUint8Array = new Uint8Array(0);
var buffer = function(lengthOrField, converter) {
  if (typeof lengthOrField === "number") {
    if (converter) {
      if (lengthOrField === 0) {
        return {
          size: 0,
          serialize: () => {
          },
          deserialize: () => converter.convert(EmptyUint8Array)
        };
      }
      return {
        size: lengthOrField,
        serialize: (value, { buffer: buffer2, index }) => {
          buffer2.set(converter.back(value).slice(0, lengthOrField), index);
        },
        deserialize: bipedal(function* (then, { reader }) {
          const array = yield* then(reader.readExactly(lengthOrField));
          return converter.convert(array);
        })
      };
    }
    if (lengthOrField === 0) {
      return {
        size: 0,
        serialize: () => {
        },
        deserialize: () => EmptyUint8Array
      };
    }
    return {
      size: lengthOrField,
      serialize: (value, { buffer: buffer2, index }) => {
        buffer2.set(value.slice(0, lengthOrField), index);
      },
      deserialize: ({ reader }) => reader.readExactly(lengthOrField)
    };
  }
  if ((typeof lengthOrField === "object" || typeof lengthOrField === "function") && "serialize" in lengthOrField) {
    if (converter) {
      return {
        size: 0,
        dynamicSize(value) {
          const array = converter.back(value);
          const lengthFieldSize = lengthOrField.dynamicSize?.(array.length) ?? lengthOrField.size;
          return lengthFieldSize + array.length;
        },
        serialize(value, context) {
          const array = converter.back(value);
          const lengthFieldSize = lengthOrField.dynamicSize?.(array.length) ?? lengthOrField.size;
          lengthOrField.serialize(array.length, context);
          context.buffer.set(array, context.index + lengthFieldSize);
        },
        deserialize: bipedal(function* (then, context) {
          const length = yield* then(lengthOrField.deserialize(context));
          const array = yield* then(context.reader.readExactly(length));
          return converter.convert(array);
        })
      };
    }
    return {
      size: 0,
      dynamicSize(value) {
        const lengthFieldSize = lengthOrField.dynamicSize?.(value.length) ?? lengthOrField.size;
        return lengthFieldSize + value.length;
      },
      serialize(value, context) {
        const lengthFieldSize = lengthOrField.dynamicSize?.(value.length) ?? lengthOrField.size;
        lengthOrField.serialize(value.length, context);
        context.buffer.set(value, context.index + lengthFieldSize);
      },
      deserialize: bipedal(function* (then, context) {
        const length = yield* then(lengthOrField.deserialize(context));
        return context.reader.readExactly(length);
      })
    };
  }
  if (typeof lengthOrField === "string") {
    if (converter) {
      return {
        size: 0,
        preSerialize: (value, runtimeStruct) => {
          runtimeStruct[lengthOrField] = converter.back(value).length;
        },
        dynamicSize: (value) => {
          return converter.back(value).length;
        },
        serialize: (value, { buffer: buffer2, index }) => {
          buffer2.set(converter.back(value), index);
        },
        deserialize: bipedal(function* (then, { reader, runtimeStruct }) {
          const length = runtimeStruct[lengthOrField];
          if (length === 0) {
            return converter.convert(EmptyUint8Array);
          }
          const value = yield* then(reader.readExactly(length));
          return converter.convert(value);
        })
      };
    }
    return {
      size: 0,
      preSerialize: (value, runtimeStruct) => {
        runtimeStruct[lengthOrField] = value.length;
      },
      dynamicSize: (value) => {
        return value.length;
      },
      serialize: (value, { buffer: buffer2, index }) => {
        buffer2.set(value, index);
      },
      deserialize: ({ reader, runtimeStruct }) => {
        const length = runtimeStruct[lengthOrField];
        if (length === 0) {
          return EmptyUint8Array;
        }
        return reader.readExactly(length);
      }
    };
  }
  if (converter) {
    return {
      size: 0,
      preSerialize: (value, runtimeStruct) => {
        const length = converter.back(value).length;
        runtimeStruct[lengthOrField.field] = lengthOrField.back(length);
      },
      dynamicSize: (value) => {
        return converter.back(value).length;
      },
      serialize: (value, { buffer: buffer2, index }) => {
        buffer2.set(converter.back(value), index);
      },
      deserialize: bipedal(function* (then, { reader, runtimeStruct }) {
        const rawLength = runtimeStruct[lengthOrField.field];
        const length = lengthOrField.convert(rawLength);
        if (length === 0) {
          return converter.convert(EmptyUint8Array);
        }
        const value = yield* then(reader.readExactly(length));
        return converter.convert(value);
      })
    };
  }
  return {
    size: 0,
    preSerialize: (value, runtimeStruct) => {
      runtimeStruct[lengthOrField.field] = lengthOrField.back(value.length);
    },
    dynamicSize: (value) => {
      return value.length;
    },
    serialize: (value, { buffer: buffer2, index }) => {
      buffer2.set(value, index);
    },
    deserialize: ({ reader, runtimeStruct }) => {
      const rawLength = runtimeStruct[lengthOrField.field];
      const length = lengthOrField.convert(rawLength);
      if (length === 0) {
        return EmptyUint8Array;
      }
      return reader.readExactly(length);
    }
  };
};

// node_modules/@yume-chan/no-data-view/esm/int16.js
function getInt16(buffer2, offset, littleEndian) {
  return littleEndian ? (buffer2[offset] | buffer2[offset + 1] << 8) << 16 >> 16 : (buffer2[offset] << 8 | buffer2[offset + 1]) << 16 >> 16;
}
function setInt16(buffer2, offset, value, littleEndian) {
  if (littleEndian) {
    buffer2[offset] = value;
    buffer2[offset + 1] = value >> 8;
  } else {
    buffer2[offset] = value >> 8;
    buffer2[offset + 1] = value;
  }
}

// node_modules/@yume-chan/no-data-view/esm/int32.js
function getInt32(buffer2, offset, littleEndian) {
  return littleEndian ? buffer2[offset] | buffer2[offset + 1] << 8 | buffer2[offset + 2] << 16 | buffer2[offset + 3] << 24 : buffer2[offset] << 24 | buffer2[offset + 1] << 16 | buffer2[offset + 2] << 8 | buffer2[offset + 3];
}
function setInt32(buffer2, offset, value, littleEndian) {
  if (littleEndian) {
    buffer2[offset] = value;
    buffer2[offset + 1] = value >> 8;
    buffer2[offset + 2] = value >> 16;
    buffer2[offset + 3] = value >> 24;
  } else {
    buffer2[offset] = value >> 24;
    buffer2[offset + 1] = value >> 16;
    buffer2[offset + 2] = value >> 8;
    buffer2[offset + 3] = value;
  }
}

// node_modules/@yume-chan/no-data-view/esm/int64.js
function getInt64(buffer2, offset, littleEndian) {
  return littleEndian ? BigInt(buffer2[offset]) | BigInt(buffer2[offset + 1]) << 8n | BigInt(buffer2[offset + 2]) << 16n | BigInt(buffer2[offset + 3]) << 24n | BigInt(buffer2[offset + 4]) << 32n | BigInt(buffer2[offset + 5]) << 40n | BigInt(buffer2[offset + 6]) << 48n | BigInt(buffer2[offset + 7] << 24) << 32n : BigInt(buffer2[offset] << 24) << 32n | BigInt(buffer2[offset + 1]) << 48n | BigInt(buffer2[offset + 2]) << 40n | BigInt(buffer2[offset + 3]) << 32n | BigInt(buffer2[offset + 4]) << 24n | BigInt(buffer2[offset + 5]) << 16n | BigInt(buffer2[offset + 6]) << 8n | BigInt(buffer2[offset + 7]);
}
function setInt64LittleEndian(buffer2, offset, value) {
  buffer2[offset] = Number(value & 0xffn);
  buffer2[offset + 1] = Number(value >> 8n & 0xffn);
  buffer2[offset + 2] = Number(value >> 16n & 0xffn);
  buffer2[offset + 3] = Number(value >> 24n & 0xffn);
  buffer2[offset + 4] = Number(value >> 32n & 0xffn);
  buffer2[offset + 5] = Number(value >> 40n & 0xffn);
  buffer2[offset + 6] = Number(value >> 48n & 0xffn);
  buffer2[offset + 7] = Number(value >> 56n & 0xffn);
}
function setInt64BigEndian(buffer2, offset, value) {
  buffer2[offset] = Number(value >> 56n & 0xffn);
  buffer2[offset + 1] = Number(value >> 48n & 0xffn);
  buffer2[offset + 2] = Number(value >> 40n & 0xffn);
  buffer2[offset + 3] = Number(value >> 32n & 0xffn);
  buffer2[offset + 4] = Number(value >> 24n & 0xffn);
  buffer2[offset + 5] = Number(value >> 16n & 0xffn);
  buffer2[offset + 6] = Number(value >> 8n & 0xffn);
  buffer2[offset + 7] = Number(value & 0xffn);
}
function setInt64(buffer2, offset, value, littleEndian) {
  if (littleEndian) {
    buffer2[offset] = Number(value & 0xffn);
    buffer2[offset + 1] = Number(value >> 8n & 0xffn);
    buffer2[offset + 2] = Number(value >> 16n & 0xffn);
    buffer2[offset + 3] = Number(value >> 24n & 0xffn);
    buffer2[offset + 4] = Number(value >> 32n & 0xffn);
    buffer2[offset + 5] = Number(value >> 40n & 0xffn);
    buffer2[offset + 6] = Number(value >> 48n & 0xffn);
    buffer2[offset + 7] = Number(value >> 56n & 0xffn);
  } else {
    buffer2[offset] = Number(value >> 56n & 0xffn);
    buffer2[offset + 1] = Number(value >> 48n & 0xffn);
    buffer2[offset + 2] = Number(value >> 40n & 0xffn);
    buffer2[offset + 3] = Number(value >> 32n & 0xffn);
    buffer2[offset + 4] = Number(value >> 24n & 0xffn);
    buffer2[offset + 5] = Number(value >> 16n & 0xffn);
    buffer2[offset + 6] = Number(value >> 8n & 0xffn);
    buffer2[offset + 7] = Number(value & 0xffn);
  }
}

// node_modules/@yume-chan/no-data-view/esm/int8.js
function getInt8(buffer2, offset) {
  return buffer2[offset] << 24 >> 24;
}

// node_modules/@yume-chan/no-data-view/esm/uint16.js
function getUint16(buffer2, offset, littleEndian) {
  return littleEndian ? buffer2[offset] | buffer2[offset + 1] << 8 : buffer2[offset + 1] | buffer2[offset] << 8;
}
function setUint16(buffer2, offset, value, littleEndian) {
  if (littleEndian) {
    buffer2[offset] = value;
    buffer2[offset + 1] = value >> 8;
  } else {
    buffer2[offset] = value >> 8;
    buffer2[offset + 1] = value;
  }
}

// node_modules/@yume-chan/no-data-view/esm/uint32.js
function getUint32LittleEndian(buffer2, offset) {
  return (buffer2[offset] | buffer2[offset + 1] << 8 | buffer2[offset + 2] << 16 | buffer2[offset + 3] << 24) >>> 0;
}
function getUint32(buffer2, offset, littleEndian) {
  return littleEndian ? (buffer2[offset] | buffer2[offset + 1] << 8 | buffer2[offset + 2] << 16 | buffer2[offset + 3] << 24) >>> 0 : (buffer2[offset] << 24 | buffer2[offset + 1] << 16 | buffer2[offset + 2] << 8 | buffer2[offset + 3]) >>> 0;
}
function setUint32LittleEndian(buffer2, offset, value) {
  buffer2[offset] = value;
  buffer2[offset + 1] = value >> 8;
  buffer2[offset + 2] = value >> 16;
  buffer2[offset + 3] = value >> 24;
}
function setUint32(buffer2, offset, value, littleEndian) {
  if (littleEndian) {
    buffer2[offset] = value;
    buffer2[offset + 1] = value >> 8;
    buffer2[offset + 2] = value >> 16;
    buffer2[offset + 3] = value >> 24;
  } else {
    buffer2[offset] = value >> 24;
    buffer2[offset + 1] = value >> 16;
    buffer2[offset + 2] = value >> 8;
    buffer2[offset + 3] = value;
  }
}

// node_modules/@yume-chan/no-data-view/esm/uint64.js
function getUint64BigEndian(buffer2, offset) {
  return BigInt(buffer2[offset]) << 56n | BigInt(buffer2[offset + 1]) << 48n | BigInt(buffer2[offset + 2]) << 40n | BigInt(buffer2[offset + 3]) << 32n | BigInt(buffer2[offset + 4]) << 24n | BigInt(buffer2[offset + 5]) << 16n | BigInt(buffer2[offset + 6]) << 8n | BigInt(buffer2[offset + 7]);
}
function getUint64(buffer2, offset, littleEndian) {
  return littleEndian ? BigInt(buffer2[offset]) | BigInt(buffer2[offset + 1]) << 8n | BigInt(buffer2[offset + 2]) << 16n | BigInt(buffer2[offset + 3]) << 24n | BigInt(buffer2[offset + 4]) << 32n | BigInt(buffer2[offset + 5]) << 40n | BigInt(buffer2[offset + 6]) << 48n | BigInt(buffer2[offset + 7]) << 56n : BigInt(buffer2[offset]) << 56n | BigInt(buffer2[offset + 1]) << 48n | BigInt(buffer2[offset + 2]) << 40n | BigInt(buffer2[offset + 3]) << 32n | BigInt(buffer2[offset + 4]) << 24n | BigInt(buffer2[offset + 5]) << 16n | BigInt(buffer2[offset + 6]) << 8n | BigInt(buffer2[offset + 7]);
}
function setUint64(buffer2, offset, value, littleEndian) {
  if (littleEndian) {
    buffer2[offset] = Number(value & 0xffn);
    buffer2[offset + 1] = Number(value >> 8n & 0xffn);
    buffer2[offset + 2] = Number(value >> 16n & 0xffn);
    buffer2[offset + 3] = Number(value >> 24n & 0xffn);
    buffer2[offset + 4] = Number(value >> 32n & 0xffn);
    buffer2[offset + 5] = Number(value >> 40n & 0xffn);
    buffer2[offset + 6] = Number(value >> 48n & 0xffn);
    buffer2[offset + 7] = Number(value >> 56n & 0xffn);
  } else {
    buffer2[offset] = Number(value >> 56n & 0xffn);
    buffer2[offset + 1] = Number(value >> 48n & 0xffn);
    buffer2[offset + 2] = Number(value >> 40n & 0xffn);
    buffer2[offset + 3] = Number(value >> 32n & 0xffn);
    buffer2[offset + 4] = Number(value >> 24n & 0xffn);
    buffer2[offset + 5] = Number(value >> 16n & 0xffn);
    buffer2[offset + 6] = Number(value >> 8n & 0xffn);
    buffer2[offset + 7] = Number(value & 0xffn);
  }
}

// node_modules/@yume-chan/struct/esm/number.js
// @__NO_SIDE_EFFECTS__
function factory(size, serialize, deserialize) {
  const result = () => result;
  result.size = size;
  result.serialize = serialize;
  result.deserialize = deserialize;
  return result;
}
var u8 = /* @__PURE__ */ factory(1, (value, { buffer: buffer2, index }) => {
  buffer2[index] = value;
}, bipedal(function* (then, { reader }) {
  const data = yield* then(reader.readExactly(1));
  return data[0];
}));
var s8 = /* @__PURE__ */ factory(1, (value, { buffer: buffer2, index }) => {
  buffer2[index] = value;
}, bipedal(function* (then, { reader }) {
  const data = yield* then(reader.readExactly(1));
  return getInt8(data, 0);
}));
var u16 = /* @__PURE__ */ factory(2, (value, { buffer: buffer2, index, littleEndian }) => {
  setUint16(buffer2, index, value, littleEndian);
}, bipedal(function* (then, { reader, littleEndian }) {
  const data = yield* then(reader.readExactly(2));
  return getUint16(data, 0, littleEndian);
}));
var s16 = /* @__PURE__ */ factory(2, (value, { buffer: buffer2, index, littleEndian }) => {
  setInt16(buffer2, index, value, littleEndian);
}, bipedal(function* (then, { reader, littleEndian }) {
  const data = yield* then(reader.readExactly(2));
  return getInt16(data, 0, littleEndian);
}));
var u32 = /* @__PURE__ */ factory(4, (value, { buffer: buffer2, index, littleEndian }) => {
  setUint32(buffer2, index, value, littleEndian);
}, bipedal(function* (then, { reader, littleEndian }) {
  const data = yield* then(reader.readExactly(4));
  return getUint32(data, 0, littleEndian);
}));
var s32 = /* @__PURE__ */ factory(4, (value, { buffer: buffer2, index, littleEndian }) => {
  setInt32(buffer2, index, value, littleEndian);
}, bipedal(function* (then, { reader, littleEndian }) {
  const data = yield* then(reader.readExactly(4));
  return getInt32(data, 0, littleEndian);
}));
var u64 = /* @__PURE__ */ factory(8, (value, { buffer: buffer2, index, littleEndian }) => {
  setUint64(buffer2, index, value, littleEndian);
}, bipedal(function* (then, { reader, littleEndian }) {
  const data = yield* then(reader.readExactly(8));
  return getUint64(data, 0, littleEndian);
}));
var s64 = /* @__PURE__ */ factory(8, (value, { buffer: buffer2, index, littleEndian }) => {
  setInt64(buffer2, index, value, littleEndian);
}, bipedal(function* (then, { reader, littleEndian }) {
  const data = yield* then(reader.readExactly(8));
  return getInt64(data, 0, littleEndian);
}));

// node_modules/@yume-chan/struct/esm/readable.js
var ExactReadableEndedError = class extends Error {
  constructor() {
    super("ExactReadable ended");
  }
};

// node_modules/@yume-chan/struct/esm/utils.js
var { TextEncoder: TextEncoder2, TextDecoder: TextDecoder2 } = globalThis;
var SharedEncoder = /* @__PURE__ */ new TextEncoder2();
var SharedDecoder = /* @__PURE__ */ new TextDecoder2();
// @__NO_SIDE_EFFECTS__
function encodeUtf8(input) {
  return SharedEncoder.encode(input);
}
// @__NO_SIDE_EFFECTS__
function decodeUtf8(buffer2) {
  return SharedDecoder.decode(buffer2);
}

// node_modules/@yume-chan/struct/esm/string.js
// @__NO_SIDE_EFFECTS__
function _string(lengthOrField) {
  const field = buffer(lengthOrField, {
    convert: decodeUtf8,
    back: encodeUtf8
  });
  field.as = () => field;
  return field;
}
var string = _string;

// node_modules/@yume-chan/struct/esm/struct.js
var StructDeserializeError = class extends Error {
  constructor(message) {
    super(message);
  }
};
var StructNotEnoughDataError = class extends StructDeserializeError {
  constructor() {
    super("The underlying readable was ended before the struct was fully deserialized");
  }
};
var StructEmptyError = class extends StructDeserializeError {
  constructor() {
    super("The underlying readable doesn't contain any more struct");
  }
};
// @__NO_SIDE_EFFECTS__
function struct(fields, options) {
  const fieldList = Object.entries(fields);
  const size = fieldList.reduce((sum, [, field]) => sum + field.size, 0);
  const littleEndian = !!options.littleEndian;
  const extra = options.extra ? Object.getOwnPropertyDescriptors(options.extra) : void 0;
  return {
    fields,
    size,
    extra: options.extra,
    serialize(runtimeStruct, buffer2) {
      for (const [key, field] of fieldList) {
        if (key in runtimeStruct) {
          field.preSerialize?.(runtimeStruct[key], runtimeStruct);
        }
      }
      const sizes = fieldList.map(([key, field]) => field.dynamicSize?.(runtimeStruct[key]) ?? field.size);
      const size2 = sizes.reduce((sum, size3) => sum + size3, 0);
      let externalBuffer = false;
      if (buffer2) {
        if (buffer2.length < size2) {
          throw new Error("Buffer too small");
        }
        externalBuffer = true;
      } else {
        buffer2 = new Uint8Array(size2);
      }
      const context = {
        buffer: buffer2,
        index: 0,
        littleEndian
      };
      for (const [index, [key, field]] of fieldList.entries()) {
        field.serialize(runtimeStruct[key], context);
        context.index += sizes[index];
      }
      if (externalBuffer) {
        return size2;
      } else {
        return buffer2;
      }
    },
    deserialize: bipedal(function* (then, reader) {
      const startPosition = reader.position;
      const runtimeStruct = {};
      const context = {
        reader,
        runtimeStruct,
        littleEndian
      };
      try {
        for (const [key, field] of fieldList) {
          runtimeStruct[key] = yield* then(field.deserialize(context));
        }
      } catch (e) {
        if (!(e instanceof ExactReadableEndedError)) {
          throw e;
        }
        if (reader.position === startPosition) {
          throw new StructEmptyError();
        } else {
          throw new StructNotEnoughDataError();
        }
      }
      if (extra) {
        Object.defineProperties(runtimeStruct, extra);
      }
      if (options.postDeserialize) {
        return options.postDeserialize.call(runtimeStruct, runtimeStruct);
      } else {
        return runtimeStruct;
      }
    })
  };
}

// node_modules/@yume-chan/stream-extra/esm/stream.js
var { AbortController } = globalThis;
var ReadableStream2 = /* @__PURE__ */ (() => {
  const { ReadableStream: ReadableStream4 } = globalThis;
  if (!ReadableStream4.from) {
    ReadableStream4.from = function(iterable) {
      const iterator = Symbol.asyncIterator in iterable ? iterable[Symbol.asyncIterator]() : iterable[Symbol.iterator]();
      return new ReadableStream4({
        async pull(controller) {
          const result = await iterator.next();
          if (result.done) {
            controller.close();
            return;
          }
          controller.enqueue(result.value);
        },
        async cancel(reason) {
          await iterator.return?.(reason);
        }
      });
    };
  }
  if (!ReadableStream4.prototype[Symbol.asyncIterator] || !ReadableStream4.prototype.values) {
    ReadableStream4.prototype.values = async function* (options) {
      const reader = this.getReader();
      try {
        while (true) {
          const { done, value } = await reader.read();
          if (done) {
            return;
          }
          yield value;
        }
      } finally {
        if (!options?.preventCancel) {
          await reader.cancel();
        }
        reader.releaseLock();
      }
    };
    ReadableStream4.prototype[Symbol.asyncIterator] = // eslint-disable-next-line @typescript-eslint/unbound-method
    ReadableStream4.prototype.values;
  }
  return ReadableStream4;
})();
var { WritableStream, TransformStream } = globalThis;

// node_modules/@yume-chan/stream-extra/esm/push-readable.js
var PushReadableStream = class extends ReadableStream2 {
  /**
   * Create a new `PushReadableStream` from a source.
   *
   * @param source If `source` returns a `Promise`, the stream will be closed
   * when the `Promise` is resolved, and be errored when the `Promise` is rejected.
   * @param strategy
   */
  constructor(source, strategy, logger) {
    let waterMarkLow;
    let zeroHighWaterMarkAllowEnqueue = false;
    const abortController = new AbortController();
    super({
      start: (controller) => {
        const result = source({
          abortSignal: abortController.signal,
          enqueue: async (chunk) => {
            logger?.({
              source: "producer",
              operation: "enqueue",
              value: chunk,
              phase: "start"
            });
            if (abortController.signal.aborted) {
              logger?.({
                source: "producer",
                operation: "enqueue",
                value: chunk,
                phase: "ignored"
              });
              return;
            }
            if (controller.desiredSize === null) {
              controller.enqueue(chunk);
              return;
            }
            if (zeroHighWaterMarkAllowEnqueue) {
              zeroHighWaterMarkAllowEnqueue = false;
              controller.enqueue(chunk);
              logger?.({
                source: "producer",
                operation: "enqueue",
                value: chunk,
                phase: "complete"
              });
              return;
            }
            if (controller.desiredSize <= 0) {
              logger?.({
                source: "producer",
                operation: "enqueue",
                value: chunk,
                phase: "waiting"
              });
              waterMarkLow = new PromiseResolver();
              await waterMarkLow.promise;
              if (abortController.signal.aborted) {
                logger?.({
                  source: "producer",
                  operation: "enqueue",
                  value: chunk,
                  phase: "ignored"
                });
                return;
              }
            }
            controller.enqueue(chunk);
            logger?.({
              source: "producer",
              operation: "enqueue",
              value: chunk,
              phase: "complete"
            });
          },
          close() {
            logger?.({
              source: "producer",
              operation: "close",
              explicit: true,
              phase: "start"
            });
            if (abortController.signal.aborted) {
              logger?.({
                source: "producer",
                operation: "close",
                explicit: true,
                phase: "ignored"
              });
              return;
            }
            controller.close();
            logger?.({
              source: "producer",
              operation: "close",
              explicit: true,
              phase: "complete"
            });
          },
          error(e) {
            logger?.({
              source: "producer",
              operation: "error",
              explicit: true,
              phase: "start"
            });
            controller.error(e);
            logger?.({
              source: "producer",
              operation: "error",
              explicit: true,
              phase: "complete"
            });
          }
        });
        if (result && "then" in result) {
          result.then(() => {
            logger?.({
              source: "producer",
              operation: "close",
              explicit: false,
              phase: "start"
            });
            try {
              controller.close();
              logger?.({
                source: "producer",
                operation: "close",
                explicit: false,
                phase: "complete"
              });
            } catch {
              logger?.({
                source: "producer",
                operation: "close",
                explicit: false,
                phase: "ignored"
              });
            }
          }, (e) => {
            logger?.({
              source: "producer",
              operation: "error",
              explicit: false,
              phase: "start"
            });
            controller.error(e);
            logger?.({
              source: "producer",
              operation: "error",
              explicit: false,
              phase: "complete"
            });
          });
        }
      },
      pull: () => {
        logger?.({
          source: "consumer",
          operation: "pull",
          phase: "start"
        });
        if (waterMarkLow) {
          waterMarkLow.resolve();
        } else if (strategy?.highWaterMark === 0) {
          zeroHighWaterMarkAllowEnqueue = true;
        }
        logger?.({
          source: "consumer",
          operation: "pull",
          phase: "complete"
        });
      },
      cancel: (reason) => {
        logger?.({
          source: "consumer",
          operation: "cancel",
          phase: "start"
        });
        abortController.abort(reason);
        waterMarkLow?.resolve();
        logger?.({
          source: "consumer",
          operation: "cancel",
          phase: "complete"
        });
      }
    }, strategy);
  }
};

// node_modules/@yume-chan/stream-extra/esm/try-close.js
function tryClose(controller) {
  try {
    controller.close();
    return true;
  } catch {
    return false;
  }
}
async function tryCancel(stream) {
  try {
    await stream.cancel();
    return true;
  } catch {
    return false;
  }
}

// node_modules/@yume-chan/stream-extra/esm/buffered.js
var BufferedReadableStream = class {
  #buffered;
  // PERF: `subarray` is slow
  // don't use it until absolutely necessary
  #bufferedOffset = 0;
  #bufferedLength = 0;
  #position = 0;
  get position() {
    return this.#position;
  }
  stream;
  reader;
  constructor(stream) {
    this.stream = stream;
    this.reader = stream.getReader();
  }
  #readBuffered(length) {
    if (!this.#buffered) {
      return void 0;
    }
    const value = this.#buffered.subarray(this.#bufferedOffset, this.#bufferedOffset + length);
    if (this.#bufferedLength > length) {
      this.#position += length;
      this.#bufferedOffset += length;
      this.#bufferedLength -= length;
      return value;
    }
    this.#position += this.#bufferedLength;
    this.#buffered = void 0;
    this.#bufferedOffset = 0;
    this.#bufferedLength = 0;
    return value;
  }
  async #readSource(length) {
    const { done, value } = await this.reader.read();
    if (done) {
      throw new ExactReadableEndedError();
    }
    if (value.length > length) {
      this.#buffered = value;
      this.#bufferedOffset = length;
      this.#bufferedLength = value.length - length;
      this.#position += length;
      return value.subarray(0, length);
    }
    this.#position += value.length;
    return value;
  }
  iterateExactly(length) {
    let state = this.#buffered ? 0 : 1;
    return {
      next: () => {
        switch (state) {
          case 0: {
            const value = this.#readBuffered(length);
            if (value.length === length) {
              state = 2;
            } else {
              length -= value.length;
              state = 1;
            }
            return { done: false, value };
          }
          case 1:
            state = 3;
            return {
              done: false,
              value: this.#readSource(length).then((value) => {
                if (value.length === length) {
                  state = 2;
                } else {
                  length -= value.length;
                  state = 1;
                }
                return value;
              })
            };
          case 2:
            return { done: true, value: void 0 };
          case 3:
            throw new Error("Can't call `next` before previous Promise resolves");
          default:
            throw new Error("unreachable");
        }
      }
    };
  }
  readExactly = bipedal(function* (then, length) {
    let result;
    let index = 0;
    const initial = this.#readBuffered(length);
    if (initial) {
      if (initial.length === length) {
        return initial;
      }
      result = new Uint8Array(length);
      result.set(initial, index);
      index += initial.length;
      length -= initial.length;
    } else {
      result = new Uint8Array(length);
    }
    while (length > 0) {
      const value = yield* then(this.#readSource(length));
      result.set(value, index);
      index += value.length;
      length -= value.length;
    }
    return result;
  });
  /**
   * Return a readable stream with unconsumed data (if any) and
   * all data from the wrapped stream.
   * @returns A `ReadableStream`
   */
  release() {
    if (this.#bufferedLength > 0) {
      return new PushReadableStream(async (controller) => {
        const buffered = this.#buffered.subarray(this.#bufferedOffset);
        await controller.enqueue(buffered);
        controller.abortSignal.addEventListener("abort", () => {
          void tryCancel(this.reader);
        });
        while (true) {
          const { done, value } = await this.reader.read();
          if (done) {
            return;
          }
          await controller.enqueue(value);
        }
      });
    } else {
      this.reader.releaseLock();
      return this.stream;
    }
  }
  async cancel(reason) {
    await this.reader.cancel(reason);
  }
};

// node_modules/@yume-chan/stream-extra/esm/buffered-transform.js
var BufferedTransformStream = class {
  #readable;
  get readable() {
    return this.#readable;
  }
  #writable;
  get writable() {
    return this.#writable;
  }
  constructor(transform) {
    let sourceStreamController;
    const buffered = new BufferedReadableStream(new PushReadableStream((controller) => {
      sourceStreamController = controller;
    }));
    this.#readable = new ReadableStream2({
      async pull(controller) {
        try {
          const value = await transform(buffered);
          controller.enqueue(value);
        } catch (e) {
          if (e instanceof StructEmptyError) {
            controller.close();
            return;
          }
          throw e;
        }
      },
      cancel: (reason) => {
        return buffered.cancel(reason);
      }
    });
    this.#writable = new WritableStream({
      async write(chunk) {
        await sourceStreamController.enqueue(chunk);
      },
      abort() {
        sourceStreamController.close();
      },
      close() {
        sourceStreamController.close();
      }
    });
  }
};

// node_modules/@yume-chan/stream-extra/esm/concat.js
var ConcatStringStream = class {
  // PERF: rope (concat strings) is faster than `[].join('')`
  #result = "";
  #resolver = new PromiseResolver();
  #writable = new WritableStream({
    write: (chunk) => {
      this.#result += chunk;
    },
    close: () => {
      this.#resolver.resolve(this.#result);
      this.#readableController.enqueue(this.#result);
      this.#readableController.close();
    },
    abort: (reason) => {
      this.#resolver.reject(reason);
      this.#readableController.error(reason);
    }
  });
  get writable() {
    return this.#writable;
  }
  #readableController;
  #readable = new ReadableStream2({
    start: (controller) => {
      this.#readableController = controller;
    }
  });
  get readable() {
    return this.#readable;
  }
  constructor() {
    void Object.defineProperties(this.#readable, {
      then: {
        get: () => this.#resolver.promise.then.bind(this.#resolver.promise)
      },
      catch: {
        get: () => this.#resolver.promise.catch.bind(this.#resolver.promise)
      },
      finally: {
        get: () => this.#resolver.promise.finally.bind(this.#resolver.promise)
      }
    });
  }
};

// node_modules/@yume-chan/stream-extra/esm/task.js
var { console: console2 } = globalThis;
var createTask = /* @__PURE__ */ (() => console2?.createTask?.bind(console2) ?? (() => ({
  run(callback) {
    return callback();
  }
})))();

// node_modules/@yume-chan/stream-extra/esm/consumable.js
var WritableStream2 = class extends WritableStream {
  static async write(writer, value) {
    const consumable = new Consumable(value);
    await writer.write(consumable);
    await consumable.consumed;
  }
  constructor(sink, strategy) {
    let wrappedStrategy;
    if (strategy) {
      wrappedStrategy = {};
      if ("highWaterMark" in strategy) {
        wrappedStrategy.highWaterMark = strategy.highWaterMark;
      }
      if ("size" in strategy) {
        wrappedStrategy.size = (chunk) => {
          return strategy.size(chunk instanceof Consumable ? chunk.value : chunk);
        };
      }
    }
    super({
      start(controller) {
        return sink.start?.(controller);
      },
      async write(chunk, controller) {
        await chunk.tryConsume((chunk2) => sink.write?.(chunk2, controller));
      },
      abort(reason) {
        return sink.abort?.(reason);
      },
      close() {
        return sink.close?.();
      }
    }, wrappedStrategy);
  }
};
var ReadableStream3 = class _ReadableStream extends ReadableStream2 {
  static async enqueue(controller, chunk) {
    const output = new Consumable(chunk);
    controller.enqueue(output);
    await output.consumed;
  }
  constructor(source, strategy) {
    let wrappedController;
    let wrappedStrategy;
    if (strategy) {
      wrappedStrategy = {};
      if ("highWaterMark" in strategy) {
        wrappedStrategy.highWaterMark = strategy.highWaterMark;
      }
      if ("size" in strategy) {
        wrappedStrategy.size = (chunk) => {
          return strategy.size(chunk.value);
        };
      }
    }
    super({
      async start(controller) {
        wrappedController = {
          async enqueue(chunk) {
            await _ReadableStream.enqueue(controller, chunk);
          },
          close() {
            controller.close();
          },
          error(reason) {
            controller.error(reason);
          }
        };
        await source.start?.(wrappedController);
      },
      async pull() {
        await source.pull?.(wrappedController);
      },
      async cancel(reason) {
        await source.cancel?.(reason);
      }
    }, wrappedStrategy);
  }
};
var Consumable = class {
  static WritableStream = WritableStream2;
  static ReadableStream = ReadableStream3;
  #task;
  #resolver;
  value;
  consumed;
  constructor(value) {
    this.#task = createTask("Consumable");
    this.value = value;
    this.#resolver = new PromiseResolver();
    this.consumed = this.#resolver.promise;
  }
  consume() {
    this.#resolver.resolve();
  }
  error(error) {
    this.#resolver.reject(error);
  }
  tryConsume(callback) {
    try {
      let result = this.#task.run(() => callback(this.value));
      if (isPromiseLike(result)) {
        result = result.then((value) => {
          this.#resolver.resolve();
          return value;
        }, (e) => {
          this.#resolver.reject(e);
          throw e;
        });
      } else {
        this.#resolver.resolve();
      }
      return result;
    } catch (e) {
      this.#resolver.reject(e);
      throw e;
    }
  }
};

// node_modules/@yume-chan/stream-extra/esm/maybe-consumable-ns.js
var maybe_consumable_ns_exports = {};
__export(maybe_consumable_ns_exports, {
  WritableStream: () => WritableStream3,
  getValue: () => getValue,
  tryConsume: () => tryConsume
});
function getValue(value) {
  return value instanceof Consumable ? value.value : value;
}
function tryConsume(value, callback) {
  if (value instanceof Consumable) {
    return value.tryConsume(callback);
  } else {
    return callback(value);
  }
}
var WritableStream3 = class extends WritableStream {
  constructor(sink, strategy) {
    let wrappedStrategy;
    if (strategy) {
      wrappedStrategy = {};
      if ("highWaterMark" in strategy) {
        wrappedStrategy.highWaterMark = strategy.highWaterMark;
      }
      if ("size" in strategy) {
        wrappedStrategy.size = (chunk) => {
          return strategy.size(chunk instanceof Consumable ? chunk.value : chunk);
        };
      }
    }
    super({
      start(controller) {
        return sink.start?.(controller);
      },
      async write(chunk, controller) {
        await tryConsume(chunk, (chunk2) => sink.write?.(chunk2, controller));
      },
      abort(reason) {
        return sink.abort?.(reason);
      },
      close() {
        return sink.close?.();
      }
    }, wrappedStrategy);
  }
};

// node_modules/@yume-chan/stream-extra/esm/distribution.js
var BufferCombiner = class {
  #capacity;
  #buffer;
  #offset;
  #available;
  constructor(size) {
    this.#capacity = size;
    this.#buffer = new Uint8Array(size);
    this.#offset = 0;
    this.#available = size;
  }
  /**
   * Pushes data to the combiner.
   * @param data The input data to be split or combined.
   * @returns
   * A generator that yields buffers of specified size.
   * It may yield the same buffer multiple times, consume the data before calling `next`.
   */
  *push(data) {
    let offset = 0;
    let available = data.length;
    if (this.#offset !== 0) {
      if (available >= this.#available) {
        this.#buffer.set(data.subarray(0, this.#available), this.#offset);
        offset += this.#available;
        available -= this.#available;
        yield this.#buffer;
        this.#offset = 0;
        this.#available = this.#capacity;
        if (available === 0) {
          return;
        }
      } else {
        this.#buffer.set(data, this.#offset);
        this.#offset += available;
        this.#available -= available;
        return;
      }
    }
    while (available >= this.#capacity) {
      const end = offset + this.#capacity;
      yield data.subarray(offset, end);
      offset = end;
      available -= this.#capacity;
    }
    if (available > 0) {
      this.#buffer.set(data.subarray(offset), this.#offset);
      this.#offset += available;
      this.#available -= available;
    }
  }
  flush() {
    if (this.#offset === 0) {
      return void 0;
    }
    const output = this.#buffer.subarray(0, this.#offset);
    this.#offset = 0;
    this.#available = this.#capacity;
    return output;
  }
};
var DistributionStream = class extends TransformStream {
  constructor(size, combine = false) {
    const combiner = combine ? new BufferCombiner(size) : void 0;
    super({
      async transform(chunk, controller) {
        await maybe_consumable_ns_exports.tryConsume(chunk, async (chunk2) => {
          if (combiner) {
            for (const buffer2 of combiner.push(chunk2)) {
              await Consumable.ReadableStream.enqueue(controller, buffer2);
            }
          } else {
            let offset = 0;
            let available = chunk2.length;
            while (available > 0) {
              const end = offset + size;
              await Consumable.ReadableStream.enqueue(controller, chunk2.subarray(offset, end));
              offset = end;
              available -= size;
            }
          }
        });
      },
      flush(controller) {
        if (combiner) {
          const data = combiner.flush();
          if (data) {
            controller.enqueue(data);
          }
        }
      }
    });
  }
};

// node_modules/@yume-chan/stream-extra/esm/wrap-readable.js
function getWrappedReadableStream(wrapper, controller) {
  if ("start" in wrapper) {
    return wrapper.start(controller);
  } else if (typeof wrapper === "function") {
    return wrapper(controller);
  } else {
    return wrapper;
  }
}
var WrapReadableStream = class extends ReadableStream2 {
  readable;
  #reader;
  constructor(wrapper, strategy) {
    super({
      start: async (controller) => {
        const readable = await getWrappedReadableStream(wrapper, controller);
        this.readable = readable;
        this.#reader = this.readable.getReader();
      },
      pull: async (controller) => {
        const { done, value } = await this.#reader.read().catch((e) => {
          if ("error" in wrapper) {
            wrapper.error(e);
          }
          throw e;
        });
        if (done) {
          controller.close();
          if ("close" in wrapper) {
            await wrapper.close?.();
          }
        } else {
          controller.enqueue(value);
        }
      },
      cancel: async (reason) => {
        await this.#reader.cancel(reason);
        if ("cancel" in wrapper) {
          await wrapper.cancel?.(reason);
        }
      }
    }, strategy);
  }
};

// node_modules/@yume-chan/stream-extra/esm/duplex.js
var NOOP = () => {
};
var DuplexStreamFactory = class {
  #readableControllers = [];
  #writers = [];
  #writableClosed = false;
  get writableClosed() {
    return this.#writableClosed;
  }
  #closed = new PromiseResolver();
  get closed() {
    return this.#closed.promise;
  }
  #options;
  constructor(options) {
    this.#options = options ?? {};
  }
  wrapReadable(readable, strategy) {
    return new WrapReadableStream({
      start: (controller) => {
        this.#readableControllers.push(controller);
        return readable;
      },
      cancel: async () => {
        await this.close();
      },
      close: async () => {
        await this.dispose();
      }
    }, strategy);
  }
  createWritable(stream) {
    const writer = stream.getWriter();
    this.#writers.push(writer);
    return new WritableStream({
      write: async (chunk) => {
        await writer.write(chunk);
      },
      abort: async (reason) => {
        await writer.abort(reason);
        await this.close();
      },
      close: async () => {
        await writer.close().catch(NOOP);
        await this.close();
      }
    });
  }
  async close() {
    if (this.#writableClosed) {
      return;
    }
    this.#writableClosed = true;
    if (await this.#options.close?.() !== false) {
      await this.dispose();
    }
    for (const writer of this.#writers) {
      writer.close().catch(NOOP);
    }
  }
  async dispose() {
    this.#writableClosed = true;
    this.#closed.resolve();
    for (const controller of this.#readableControllers) {
      tryClose(controller);
    }
    await this.#options.dispose?.();
  }
};

// node_modules/@yume-chan/stream-extra/esm/encoding.js
var Global = globalThis;
var TextDecoderStream = Global.TextDecoderStream;
var TextEncoderStream = Global.TextEncoderStream;

// node_modules/@yume-chan/stream-extra/esm/pipe-from.js
function pipeFrom(writable, pair) {
  const writer = pair.writable.getWriter();
  const pipe = pair.readable.pipeTo(writable);
  return new WritableStream({
    async write(chunk) {
      await writer.write(chunk);
    },
    async close() {
      await writer.close();
      await pipe;
    }
  });
}

// node_modules/@yume-chan/stream-extra/esm/struct-deserialize.js
var StructDeserializeStream = class extends BufferedTransformStream {
  constructor(struct2) {
    super((stream) => {
      return struct2.deserialize(stream);
    });
  }
};

// node_modules/@yume-chan/event/esm/disposable.js
var AutoDisposable = class {
  #disposables = [];
  constructor() {
    this.dispose = this.dispose.bind(this);
  }
  addDisposable(disposable) {
    this.#disposables.push(disposable);
    return disposable;
  }
  dispose() {
    for (const disposable of this.#disposables) {
      disposable.dispose();
    }
    this.#disposables = [];
  }
};

// node_modules/@yume-chan/event/esm/event-emitter.js
var EventEmitter = class {
  listeners = [];
  constructor() {
    this.event = this.event.bind(this);
  }
  addEventListener(info) {
    this.listeners.push(info);
    const remove = () => {
      const index = this.listeners.indexOf(info);
      if (index !== -1) {
        this.listeners.splice(index, 1);
      }
    };
    remove.dispose = remove;
    return remove;
  }
  event = (listener, thisArg, ...args) => {
    const info = {
      listener,
      thisArg,
      args
    };
    return this.addEventListener(info);
  };
  fire(e) {
    for (const info of this.listeners.slice()) {
      info.listener.apply(info.thisArg, [e, ...info.args]);
    }
  }
  dispose() {
    this.listeners.length = 0;
  }
};

// node_modules/@yume-chan/adb/esm/commands/base.js
var AdbCommandBase = class extends AutoDisposable {
  adb;
  constructor(adb2) {
    super();
    this.adb = adb2;
  }
};

// node_modules/@yume-chan/adb/esm/commands/framebuffer.js
var Version = struct({ version: u32 }, { littleEndian: true });
var AdbFrameBufferV1 = struct({
  bpp: u32,
  size: u32,
  width: u32,
  height: u32,
  red_offset: u32,
  red_length: u32,
  blue_offset: u32,
  blue_length: u32,
  green_offset: u32,
  green_length: u32,
  alpha_offset: u32,
  alpha_length: u32,
  data: buffer("size")
}, { littleEndian: true });
var AdbFrameBufferV2 = struct({
  bpp: u32,
  colorSpace: u32,
  size: u32,
  width: u32,
  height: u32,
  red_offset: u32,
  red_length: u32,
  blue_offset: u32,
  blue_length: u32,
  green_offset: u32,
  green_length: u32,
  alpha_offset: u32,
  alpha_length: u32,
  data: buffer("size")
}, { littleEndian: true });
var AdbFrameBufferError = class extends Error {
  constructor(message, options) {
    super(message, options);
  }
};
var AdbFrameBufferUnsupportedVersionError = class extends AdbFrameBufferError {
  constructor(version) {
    super(`Unsupported FrameBuffer version ${version}`);
  }
};
var AdbFrameBufferForbiddenError = class extends AdbFrameBufferError {
  constructor() {
    super("FrameBuffer is disabled by current app");
  }
};
async function framebuffer(adb2) {
  const socket = await adb2.createSocket("framebuffer:");
  const stream = new BufferedReadableStream(socket.readable);
  let version;
  try {
    ({ version } = await Version.deserialize(stream));
  } catch (e) {
    if (e instanceof StructEmptyError) {
      throw new AdbFrameBufferForbiddenError();
    }
    throw e;
  }
  switch (version) {
    case 1:
      return await AdbFrameBufferV1.deserialize(stream);
    case 2:
      return await AdbFrameBufferV2.deserialize(stream);
    default:
      throw new AdbFrameBufferUnsupportedVersionError(version);
  }
}

// node_modules/@yume-chan/adb/esm/commands/power.js
var AdbPower = class extends AdbCommandBase {
  reboot(mode = "") {
    return this.adb.createSocketAndWait(`reboot:${mode}`);
  }
  bootloader() {
    return this.reboot("bootloader");
  }
  fastboot() {
    return this.reboot("fastboot");
  }
  recovery() {
    return this.reboot("recovery");
  }
  sideload() {
    return this.reboot("sideload");
  }
  /**
   * Reboot to Qualcomm Emergency Download (EDL) Mode.
   *
   * Only works on some Qualcomm devices.
   */
  qualcommEdlMode() {
    return this.reboot("edl");
  }
  powerOff() {
    return this.adb.subprocess.spawnAndWaitLegacy(["reboot", "-p"]);
  }
  powerButton(longPress = false) {
    const args = ["input", "keyevent"];
    if (longPress) {
      args.push("--longpress");
    }
    args.push("POWER");
    return this.adb.subprocess.spawnAndWaitLegacy(args);
  }
  /**
   * Reboot to Samsung Odin download mode.
   *
   * Only works on Samsung devices.
   */
  samsungOdin() {
    return this.reboot("download");
  }
};

// node_modules/@yume-chan/adb/esm/utils/auto-reset-event.js
var AutoResetEvent = class {
  #set;
  #queue = [];
  constructor(initialSet = false) {
    this.#set = initialSet;
  }
  wait() {
    if (!this.#set) {
      this.#set = true;
      if (this.#queue.length === 0) {
        return Promise.resolve();
      }
    }
    const resolver = new PromiseResolver();
    this.#queue.push(resolver);
    return resolver.promise;
  }
  notifyOne() {
    if (this.#queue.length !== 0) {
      this.#queue.pop().resolve();
    } else {
      this.#set = false;
    }
  }
  dispose() {
    for (const item of this.#queue) {
      item.reject(new Error("The AutoResetEvent has been disposed"));
    }
    this.#queue.length = 0;
  }
};

// node_modules/@yume-chan/adb/esm/utils/base64.js
var [charToIndex, indexToChar, paddingChar] = /* @__PURE__ */ (() => {
  const charToIndex2 = [];
  const indexToChar2 = [];
  const paddingChar2 = "=".charCodeAt(0);
  function addRange(start, end) {
    const charCodeStart = start.charCodeAt(0);
    const charCodeEnd = end.charCodeAt(0);
    for (let charCode = charCodeStart; charCode <= charCodeEnd; charCode += 1) {
      charToIndex2[charCode] = indexToChar2.length;
      indexToChar2.push(charCode);
    }
  }
  addRange("A", "Z");
  addRange("a", "z");
  addRange("0", "9");
  addRange("+", "+");
  addRange("/", "/");
  return [charToIndex2, indexToChar2, paddingChar2];
})();
function calculateBase64EncodedLength(inputLength) {
  const remainder = inputLength % 3;
  const paddingLength = remainder !== 0 ? 3 - remainder : 0;
  return [(inputLength + paddingLength) / 3 * 4, paddingLength];
}
function encodeBase64(input, output) {
  const [outputLength, paddingLength] = calculateBase64EncodedLength(input.length);
  if (!output) {
    output = new Uint8Array(outputLength);
    encodeForward(input, output, paddingLength);
    return output;
  } else {
    if (output.length < outputLength) {
      throw new TypeError("output buffer is too small");
    }
    output = output.subarray(0, outputLength);
    if (input.buffer !== output.buffer) {
      encodeForward(input, output, paddingLength);
    } else if (output.byteOffset + output.length - (paddingLength + 1) <= input.byteOffset + input.length) {
      encodeForward(input, output, paddingLength);
    } else if (output.byteOffset >= input.byteOffset - 1) {
      encodeBackward(input, output, paddingLength);
    } else {
      throw new TypeError("input and output cannot overlap");
    }
    return outputLength;
  }
}
function encodeForward(input, output, paddingLength) {
  let inputIndex = 0;
  let outputIndex = 0;
  while (inputIndex < input.length - 2) {
    const x = input[inputIndex];
    inputIndex += 1;
    const y = input[inputIndex];
    inputIndex += 1;
    const z = input[inputIndex];
    inputIndex += 1;
    output[outputIndex] = indexToChar[x >> 2];
    outputIndex += 1;
    output[outputIndex] = indexToChar[(x & 3) << 4 | y >> 4];
    outputIndex += 1;
    output[outputIndex] = indexToChar[(y & 15) << 2 | z >> 6];
    outputIndex += 1;
    output[outputIndex] = indexToChar[z & 63];
    outputIndex += 1;
  }
  if (paddingLength === 2) {
    const x = input[inputIndex];
    inputIndex += 1;
    output[outputIndex] = indexToChar[x >> 2];
    outputIndex += 1;
    output[outputIndex] = indexToChar[(x & 3) << 4];
    outputIndex += 1;
    output[outputIndex] = paddingChar;
    outputIndex += 1;
    output[outputIndex] = paddingChar;
  } else if (paddingLength === 1) {
    const x = input[inputIndex];
    inputIndex += 1;
    const y = input[inputIndex];
    inputIndex += 1;
    output[outputIndex] = indexToChar[x >> 2];
    outputIndex += 1;
    output[outputIndex] = indexToChar[(x & 3) << 4 | y >> 4];
    outputIndex += 1;
    output[outputIndex] = indexToChar[(y & 15) << 2];
    outputIndex += 1;
    output[outputIndex] = paddingChar;
  }
}
function encodeBackward(input, output, paddingLength) {
  let inputIndex = input.length - 1;
  let outputIndex = output.length - 1;
  if (paddingLength === 2) {
    const x = input[inputIndex];
    inputIndex -= 1;
    output[outputIndex] = paddingChar;
    outputIndex -= 1;
    output[outputIndex] = paddingChar;
    outputIndex -= 1;
    output[outputIndex] = indexToChar[(x & 3) << 4];
    outputIndex -= 1;
    output[outputIndex] = indexToChar[x >> 2];
    outputIndex -= 1;
  } else if (paddingLength === 1) {
    const y = input[inputIndex];
    inputIndex -= 1;
    const x = input[inputIndex];
    inputIndex -= 1;
    output[outputIndex] = paddingChar;
    outputIndex -= 1;
    output[outputIndex] = indexToChar[(y & 15) << 2];
    outputIndex -= 1;
    output[outputIndex] = indexToChar[(x & 3) << 4 | y >> 4];
    outputIndex -= 1;
    output[outputIndex] = indexToChar[x >> 2];
    outputIndex -= 1;
  }
  while (inputIndex >= 0) {
    const z = input[inputIndex];
    inputIndex -= 1;
    const y = input[inputIndex];
    inputIndex -= 1;
    const x = input[inputIndex];
    inputIndex -= 1;
    output[outputIndex] = indexToChar[z & 63];
    outputIndex -= 1;
    output[outputIndex] = indexToChar[(y & 15) << 2 | z >> 6];
    outputIndex -= 1;
    output[outputIndex] = indexToChar[(x & 3) << 4 | y >> 4];
    outputIndex -= 1;
    output[outputIndex] = indexToChar[x >> 2];
    outputIndex -= 1;
  }
}

// node_modules/@yume-chan/adb/esm/utils/hex.js
function hexCharToNumber(char) {
  if (char < 48) {
    throw new TypeError(`Invalid hex char ${char}`);
  }
  if (char < 58) {
    return char - 48;
  }
  if (char < 65) {
    throw new TypeError(`Invalid hex char ${char}`);
  }
  if (char < 71) {
    return char - 55;
  }
  if (char < 97) {
    throw new TypeError(`Invalid hex char ${char}`);
  }
  if (char < 103) {
    return char - 87;
  }
  throw new TypeError(`Invalid hex char ${char}`);
}
function hexToNumber(data) {
  let result = 0;
  for (let i = 0; i < data.length; i += 1) {
    result = result << 4 | hexCharToNumber(data[i]);
  }
  return result;
}

// node_modules/@yume-chan/adb/esm/utils/no-op.js
var NOOP2 = /* @__NO_SIDE_EFFECTS__ */ () => {
};
function unreachable(...args) {
  throw new Error("Unreachable. Arguments:\n" + args.join("\n"));
}

// node_modules/@yume-chan/adb/esm/utils/sequence-equal.js
function sequenceEqual(a, b) {
  if (a.length !== b.length) {
    return false;
  }
  for (let i = 0; i < a.length; i += 1) {
    if (a[i] !== b[i]) {
      return false;
    }
  }
  return true;
}

// node_modules/@yume-chan/adb/esm/commands/reverse.js
var AdbReverseStringResponse = struct({
  length: string(4),
  content: string({
    field: "length",
    convert(value) {
      return Number.parseInt(value, 16);
    },
    back(value) {
      return value.toString(16).padStart(4, "0");
    }
  })
}, { littleEndian: true });
var AdbReverseError = class extends Error {
  constructor(message) {
    super(message);
  }
};
var AdbReverseNotSupportedError = class extends AdbReverseError {
  constructor() {
    super("ADB reverse tunnel is not supported on this device when connected wirelessly.");
  }
};
var AdbReverseErrorResponse = struct(
  /* @__PURE__ */ (() => AdbReverseStringResponse.fields)(),
  {
    littleEndian: true,
    postDeserialize: (value) => {
      if (value.content === "more than one device/emulator") {
        throw new AdbReverseNotSupportedError();
      } else {
        throw new AdbReverseError(value.content);
      }
    }
  }
);
function decimalToNumber(buffer2) {
  let value = 0;
  for (const byte of buffer2) {
    if (byte < 48 || byte > 57) {
      return value;
    }
    value = value * 10 + byte - 48;
  }
  return value;
}
var OKAY = encodeUtf8("OKAY");
var AdbReverseCommand = class {
  adb;
  #deviceAddressToLocalAddress = /* @__PURE__ */ new Map();
  constructor(adb2) {
    this.adb = adb2;
  }
  async createBufferedStream(service) {
    const socket = await this.adb.createSocket(service);
    return new BufferedReadableStream(socket.readable);
  }
  async sendRequest(service) {
    const stream = await this.createBufferedStream(service);
    const response = await stream.readExactly(4);
    if (!sequenceEqual(response, OKAY)) {
      await AdbReverseErrorResponse.deserialize(stream);
    }
    return stream;
  }
  /**
   * Get a list of all reverse port forwarding on the device.
   */
  async list() {
    const stream = await this.createBufferedStream("reverse:list-forward");
    const response = await AdbReverseStringResponse.deserialize(stream);
    return response.content.split("\n").filter((line) => !!line).map((line) => {
      const [deviceSerial, localName, remoteName] = line.split(" ");
      return { deviceSerial, localName, remoteName };
    });
  }
  /**
   * Add a reverse port forwarding for a program that already listens on a port.
   */
  async addExternal(deviceAddress, localAddress) {
    const stream = await this.sendRequest(`reverse:forward:${deviceAddress};${localAddress}`);
    if (deviceAddress.startsWith("tcp:")) {
      const position = stream.position;
      try {
        const length = hexToNumber(await stream.readExactly(4));
        const port = decimalToNumber(await stream.readExactly(length));
        deviceAddress = `tcp:${port}`;
      } catch (e) {
        if (e instanceof ExactReadableEndedError && stream.position === position) {
        } else {
          throw e;
        }
      }
    }
    return deviceAddress;
  }
  /**
   * Add a reverse port forwarding.
   */
  async add(deviceAddress, handler, localAddress) {
    localAddress = await this.adb.transport.addReverseTunnel(handler, localAddress);
    try {
      deviceAddress = await this.addExternal(deviceAddress, localAddress);
      this.#deviceAddressToLocalAddress.set(deviceAddress, localAddress);
      return deviceAddress;
    } catch (e) {
      await this.adb.transport.removeReverseTunnel(localAddress);
      throw e;
    }
  }
  /**
   * Remove a reverse port forwarding.
   */
  async remove(deviceAddress) {
    const localAddress = this.#deviceAddressToLocalAddress.get(deviceAddress);
    if (localAddress) {
      await this.adb.transport.removeReverseTunnel(localAddress);
    }
    await this.sendRequest(`reverse:killforward:${deviceAddress}`);
  }
  /**
   * Remove all reverse port forwarding, including the ones added by other programs.
   */
  async removeAll() {
    await this.adb.transport.clearReverseTunnels();
    this.#deviceAddressToLocalAddress.clear();
    await this.sendRequest(`reverse:killforward-all`);
  }
};

// node_modules/@yume-chan/adb/esm/commands/subprocess/protocols/none.js
var AdbSubprocessNoneProtocol = class _AdbSubprocessNoneProtocol {
  static isSupported() {
    return true;
  }
  static async pty(adb2, command) {
    return new _AdbSubprocessNoneProtocol(await adb2.createSocket(`shell:${command}`));
  }
  static async raw(adb2, command) {
    return new _AdbSubprocessNoneProtocol(await adb2.createSocket(`exec:${command}`));
  }
  #socket;
  // Legacy shell forwards all data to stdin.
  get stdin() {
    return this.#socket.writable;
  }
  /**
   * Legacy shell mixes stdout and stderr.
   */
  get stdout() {
    return this.#socket.readable;
  }
  #stderr;
  /**
   * `stderr` will always be empty.
   */
  get stderr() {
    return this.#stderr;
  }
  #exit;
  get exit() {
    return this.#exit;
  }
  constructor(socket) {
    this.#socket = socket;
    this.#stderr = new ReadableStream2({
      start: async (controller) => {
        await this.#socket.closed;
        controller.close();
      }
    });
    this.#exit = socket.closed.then(() => 0);
  }
  resize() {
  }
  async kill() {
    await this.#socket.close();
  }
};

// node_modules/@yume-chan/adb/esm/features.js
var AdbFeature = {
  ShellV2: "shell_v2",
  Cmd: "cmd",
  StatV2: "stat_v2",
  ListV2: "ls_v2",
  FixedPushMkdir: "fixed_push_mkdir",
  Abb: "abb",
  AbbExec: "abb_exec",
  SendReceiveV2: "sendrecv_v2",
  DelayedAck: "delayed_ack"
};

// node_modules/@yume-chan/adb/esm/commands/subprocess/protocols/shell.js
var AdbShellProtocolId = {
  Stdin: 0,
  Stdout: 1,
  Stderr: 2,
  Exit: 3,
  CloseStdin: 4,
  WindowSizeChange: 5
};
var AdbShellProtocolPacket = struct({
  id: u8(),
  data: buffer(u32)
}, { littleEndian: true });
var AdbSubprocessShellProtocol = class _AdbSubprocessShellProtocol {
  static isSupported(adb2) {
    return adb2.canUseFeature(AdbFeature.ShellV2);
  }
  static async pty(adb2, command) {
    return new _AdbSubprocessShellProtocol(await adb2.createSocket(`shell,v2,pty:${command}`));
  }
  static async raw(adb2, command) {
    return new _AdbSubprocessShellProtocol(await adb2.createSocket(`shell,v2,raw:${command}`));
  }
  #socket;
  #writer;
  #stdin;
  get stdin() {
    return this.#stdin;
  }
  #stdout;
  get stdout() {
    return this.#stdout;
  }
  #stderr;
  get stderr() {
    return this.#stderr;
  }
  #exit = new PromiseResolver();
  get exit() {
    return this.#exit.promise;
  }
  constructor(socket) {
    this.#socket = socket;
    let stdoutController;
    let stderrController;
    this.#stdout = new PushReadableStream((controller) => {
      stdoutController = controller;
    });
    this.#stderr = new PushReadableStream((controller) => {
      stderrController = controller;
    });
    socket.readable.pipeThrough(new StructDeserializeStream(AdbShellProtocolPacket)).pipeTo(new WritableStream({
      write: async (chunk) => {
        switch (chunk.id) {
          case AdbShellProtocolId.Exit:
            this.#exit.resolve(chunk.data[0]);
            break;
          case AdbShellProtocolId.Stdout:
            await stdoutController.enqueue(chunk.data);
            break;
          case AdbShellProtocolId.Stderr:
            await stderrController.enqueue(chunk.data);
            break;
        }
      }
    })).then(() => {
      stdoutController.close();
      stderrController.close();
      this.#exit.reject(new Error("Socket ended without exit message"));
    }, (e) => {
      stdoutController.error(e);
      stderrController.error(e);
      this.#exit.reject(e);
    });
    this.#writer = this.#socket.writable.getWriter();
    this.#stdin = new maybe_consumable_ns_exports.WritableStream({
      write: async (chunk) => {
        await this.#writer.write(AdbShellProtocolPacket.serialize({
          id: AdbShellProtocolId.Stdin,
          data: chunk
        }));
      }
    });
  }
  async resize(rows, cols) {
    await this.#writer.write(AdbShellProtocolPacket.serialize({
      id: AdbShellProtocolId.WindowSizeChange,
      // The "correct" format is `${rows}x${cols},${x_pixels}x${y_pixels}`
      // However, according to https://linux.die.net/man/4/tty_ioctl
      // `x_pixels` and `y_pixels` are unused, so always sending `0` should be fine.
      data: encodeUtf8(`${rows}x${cols},0x0\0`)
    }));
  }
  kill() {
    return this.#socket.close();
  }
};

// node_modules/@yume-chan/adb/esm/commands/subprocess/command.js
var DEFAULT_OPTIONS = {
  protocols: [AdbSubprocessShellProtocol, AdbSubprocessNoneProtocol]
};
var AdbSubprocess = class extends AdbCommandBase {
  async #createProtocol(mode, command, options) {
    const { protocols } = { ...DEFAULT_OPTIONS, ...options };
    let Constructor;
    for (const item of protocols) {
      if (await item.isSupported(this.adb)) {
        Constructor = item;
        break;
      }
    }
    if (!Constructor) {
      throw new Error("No specified protocol is supported by the device");
    }
    if (Array.isArray(command)) {
      command = command.join(" ");
    } else if (command === void 0) {
      command = "";
    }
    return await Constructor[mode](this.adb, command);
  }
  /**
   * Spawns an executable in PTY mode.
   *
   * Redirection mode is enough for most simple commands, but PTY mode is required for
   * commands that manipulate the terminal, such as `vi` and `less`.
   * @param command The command to run. If omitted, the default shell will be spawned.
   * @param options The options for creating the `AdbSubprocessProtocol`
   * @returns A new `AdbSubprocessProtocol` instance connecting to the spawned process.
   */
  shell(command, options) {
    return this.#createProtocol("pty", command, options);
  }
  /**
   * Spawns an executable and redirect the standard input/output stream.
   *
   * Redirection mode is enough for most simple commands, but PTY mode is required for
   * commands that manipulate the terminal, such as `vi` and `less`.
   * @param command The command to run, or an array of strings containing both command and args.
   * @param options The options for creating the `AdbSubprocessProtocol`
   * @returns A new `AdbSubprocessProtocol` instance connecting to the spawned process.
   */
  spawn(command, options) {
    return this.#createProtocol("raw", command, options);
  }
  /**
   * Spawns a new process, waits until it exits, and returns the entire output.
   * @param command The command to run
   * @param options The options for creating the `AdbSubprocessProtocol`
   * @returns The entire output of the command
   */
  async spawnAndWait(command, options) {
    const process = await this.spawn(command, options);
    const [stdout, stderr, exitCode] = await Promise.all([
      process.stdout.pipeThrough(new TextDecoderStream()).pipeThrough(new ConcatStringStream()),
      process.stderr.pipeThrough(new TextDecoderStream()).pipeThrough(new ConcatStringStream()),
      process.exit
    ]);
    return {
      stdout,
      stderr,
      exitCode
    };
  }
  /**
   * Spawns a new process, waits until it exits, and returns the entire output.
   * @param command The command to run
   * @returns The entire output of the command
   */
  async spawnAndWaitLegacy(command) {
    const { stdout } = await this.spawnAndWait(command, {
      protocols: [AdbSubprocessNoneProtocol]
    });
    return stdout;
  }
};

// node_modules/@yume-chan/adb/esm/commands/subprocess/utils.js
function escapeArg(s) {
  let result = "";
  result += `'`;
  let base = 0;
  while (true) {
    const found = s.indexOf(`'`, base);
    if (found === -1) {
      result += s.substring(base);
      break;
    }
    result += s.substring(base, found);
    result += String.raw`'\''`;
    base = found + 1;
  }
  result += `'`;
  return result;
}

// node_modules/@yume-chan/adb/esm/commands/sync/response.js
function encodeAsciiUnchecked(value) {
  const result = new Uint8Array(value.length);
  for (let i = 0; i < value.length; i += 1) {
    result[i] = value.charCodeAt(i);
  }
  return result;
}
// @__NO_SIDE_EFFECTS__
function adbSyncEncodeId(value) {
  const buffer2 = encodeAsciiUnchecked(value);
  return getUint32LittleEndian(buffer2, 0);
}
var AdbSyncResponseId = {
  Entry: /* @__PURE__ */ adbSyncEncodeId("DENT"),
  Entry2: /* @__PURE__ */ adbSyncEncodeId("DNT2"),
  Lstat: /* @__PURE__ */ adbSyncEncodeId("STAT"),
  Stat: /* @__PURE__ */ adbSyncEncodeId("STA2"),
  Lstat2: /* @__PURE__ */ adbSyncEncodeId("LST2"),
  Done: /* @__PURE__ */ adbSyncEncodeId("DONE"),
  Data: /* @__PURE__ */ adbSyncEncodeId("DATA"),
  Ok: /* @__PURE__ */ adbSyncEncodeId("OKAY"),
  Fail: /* @__PURE__ */ adbSyncEncodeId("FAIL")
};
var AdbSyncError = class extends Error {
};
var AdbSyncFailResponse = struct({ message: string(u32) }, {
  littleEndian: true,
  postDeserialize(value) {
    throw new AdbSyncError(value.message);
  }
});
async function adbSyncReadResponse(stream, id, type) {
  if (typeof id === "string") {
    id = /* @__PURE__ */ adbSyncEncodeId(id);
  }
  const buffer2 = await stream.readExactly(4);
  switch (getUint32LittleEndian(buffer2, 0)) {
    case AdbSyncResponseId.Fail:
      await AdbSyncFailResponse.deserialize(stream);
      throw new Error("Unreachable");
    case id:
      return await type.deserialize(stream);
    default:
      throw new Error(`Expected '${id}', but got '${decodeUtf8(buffer2)}'`);
  }
}
async function* adbSyncReadResponses(stream, id, type) {
  if (typeof id === "string") {
    id = /* @__PURE__ */ adbSyncEncodeId(id);
  }
  while (true) {
    const buffer2 = await stream.readExactly(4);
    switch (getUint32LittleEndian(buffer2, 0)) {
      case AdbSyncResponseId.Fail:
        await AdbSyncFailResponse.deserialize(stream);
        throw new Error("Unreachable");
      case AdbSyncResponseId.Done:
        await stream.readExactly(type.size);
        return;
      case id:
        yield await type.deserialize(stream);
        break;
      default:
        throw new Error(`Expected '${id}' or '${AdbSyncResponseId.Done}', but got '${decodeUtf8(buffer2)}'`);
    }
  }
}

// node_modules/@yume-chan/adb/esm/commands/sync/request.js
var AdbSyncRequestId = {
  List: adbSyncEncodeId("LIST"),
  ListV2: adbSyncEncodeId("LIS2"),
  Send: adbSyncEncodeId("SEND"),
  SendV2: adbSyncEncodeId("SND2"),
  Lstat: adbSyncEncodeId("STAT"),
  Stat: adbSyncEncodeId("STA2"),
  LstatV2: adbSyncEncodeId("LST2"),
  Data: adbSyncEncodeId("DATA"),
  Done: adbSyncEncodeId("DONE"),
  Receive: adbSyncEncodeId("RECV")
};
var AdbSyncNumberRequest = struct({ id: u32, arg: u32 }, { littleEndian: true });
async function adbSyncWriteRequest(writable, id, value) {
  if (typeof id === "string") {
    id = adbSyncEncodeId(id);
  }
  if (typeof value === "number") {
    await writable.write(AdbSyncNumberRequest.serialize({ id, arg: value }));
    return;
  }
  if (typeof value === "string") {
    value = encodeUtf8(value);
  }
  await writable.write(AdbSyncNumberRequest.serialize({ id, arg: value.length }));
  await writable.write(value);
}

// node_modules/@yume-chan/adb/esm/commands/sync/stat.js
var LinuxFileType = {
  Directory: 4,
  File: 8,
  Link: 10
};
var AdbSyncLstatResponse = struct({ mode: u32, size: u32, mtime: u32 }, {
  littleEndian: true,
  extra: {
    get type() {
      return this.mode >> 12;
    },
    get permission() {
      return this.mode & 4095;
    }
  },
  postDeserialize(value) {
    if (value.mode === 0 && value.size === 0 && value.mtime === 0) {
      throw new Error("lstat error");
    }
    return value;
  }
});
var AdbSyncStatErrorCode = {
  SUCCESS: 0,
  EACCES: 13,
  EEXIST: 17,
  EFAULT: 14,
  EFBIG: 27,
  EINTR: 4,
  EINVAL: 22,
  EIO: 5,
  EISDIR: 21,
  ELOOP: 40,
  EMFILE: 24,
  ENAMETOOLONG: 36,
  ENFILE: 23,
  ENOENT: 2,
  ENOMEM: 12,
  ENOSPC: 28,
  ENOTDIR: 20,
  EOVERFLOW: 75,
  EPERM: 1,
  EROFS: 30,
  ETXTBSY: 26
};
var AdbSyncStatErrorName = /* @__PURE__ */ (() => Object.fromEntries(Object.entries(AdbSyncStatErrorCode).map(([key, value]) => [
  value,
  key
])))();
var AdbSyncStatResponse = struct({
  error: u32(),
  dev: u64,
  ino: u64,
  mode: u32,
  nlink: u32,
  uid: u32,
  gid: u32,
  size: u64,
  atime: u64,
  mtime: u64,
  ctime: u64
}, {
  littleEndian: true,
  extra: {
    get type() {
      return this.mode >> 12;
    },
    get permission() {
      return this.mode & 4095;
    }
  },
  postDeserialize(value) {
    if (value.error) {
      throw new Error(AdbSyncStatErrorName[value.error]);
    }
    return value;
  }
});
async function adbSyncLstat(socket, path, v2) {
  const locked = await socket.lock();
  try {
    if (v2) {
      await adbSyncWriteRequest(locked, AdbSyncRequestId.LstatV2, path);
      return await adbSyncReadResponse(locked, AdbSyncResponseId.Lstat2, AdbSyncStatResponse);
    } else {
      await adbSyncWriteRequest(locked, AdbSyncRequestId.Lstat, path);
      const response = await adbSyncReadResponse(locked, AdbSyncResponseId.Lstat, AdbSyncLstatResponse);
      return {
        mode: response.mode,
        // Convert to `BigInt` to make it compatible with `AdbSyncStatResponse`
        size: BigInt(response.size),
        mtime: BigInt(response.mtime),
        get type() {
          return response.type;
        },
        get permission() {
          return response.permission;
        }
      };
    }
  } finally {
    locked.release();
  }
}
async function adbSyncStat(socket, path) {
  const locked = await socket.lock();
  try {
    await adbSyncWriteRequest(locked, AdbSyncRequestId.Stat, path);
    return await adbSyncReadResponse(locked, AdbSyncResponseId.Stat, AdbSyncStatResponse);
  } finally {
    locked.release();
  }
}

// node_modules/@yume-chan/adb/esm/commands/sync/list.js
var AdbSyncEntryResponse = /* @__PURE__ */ (() => struct({
  ...AdbSyncLstatResponse.fields,
  name: string(u32)
}, { littleEndian: true, extra: AdbSyncLstatResponse.extra }))();
var AdbSyncEntry2Response = /* @__PURE__ */ (() => struct({
  ...AdbSyncStatResponse.fields,
  name: string(u32)
}, { littleEndian: true, extra: AdbSyncStatResponse.extra }))();
async function* adbSyncOpenDirV2(socket, path) {
  const locked = await socket.lock();
  try {
    await adbSyncWriteRequest(locked, AdbSyncRequestId.ListV2, path);
    for await (const item of adbSyncReadResponses(locked, AdbSyncResponseId.Entry2, AdbSyncEntry2Response)) {
      if (item.error !== AdbSyncStatErrorCode.SUCCESS) {
        continue;
      }
      yield item;
    }
  } finally {
    locked.release();
  }
}
async function* adbSyncOpenDirV1(socket, path) {
  const locked = await socket.lock();
  try {
    await adbSyncWriteRequest(locked, AdbSyncRequestId.List, path);
    for await (const item of adbSyncReadResponses(locked, AdbSyncResponseId.Entry, AdbSyncEntryResponse)) {
      yield item;
    }
  } finally {
    locked.release();
  }
}
async function* adbSyncOpenDir(socket, path, v2) {
  if (v2) {
    yield* adbSyncOpenDirV2(socket, path);
  } else {
    for await (const item of adbSyncOpenDirV1(socket, path)) {
      yield {
        mode: item.mode,
        size: BigInt(item.size),
        mtime: BigInt(item.mtime),
        get type() {
          return item.type;
        },
        get permission() {
          return item.permission;
        },
        name: item.name
      };
    }
  }
}

// node_modules/@yume-chan/adb/esm/commands/sync/pull.js
var AdbSyncDataResponse = struct({ data: buffer(u32) }, { littleEndian: true });
async function* adbSyncPullGenerator(socket, path) {
  const locked = await socket.lock();
  let done = false;
  try {
    await adbSyncWriteRequest(locked, AdbSyncRequestId.Receive, path);
    for await (const packet of adbSyncReadResponses(locked, AdbSyncResponseId.Data, AdbSyncDataResponse)) {
      yield packet.data;
    }
    done = true;
  } catch (e) {
    done = true;
    throw e;
  } finally {
    if (!done) {
      for await (const packet of adbSyncReadResponses(locked, AdbSyncResponseId.Data, AdbSyncDataResponse)) {
        void packet;
      }
    }
    locked.release();
  }
}
function adbSyncPull(socket, path) {
  return new PushReadableStream(async (controller) => {
    for await (const data of adbSyncPullGenerator(socket, path)) {
      await controller.enqueue(data);
    }
  });
}

// node_modules/@yume-chan/adb/esm/commands/sync/push.js
var ADB_SYNC_MAX_PACKET_SIZE = 64 * 1024;
var AdbSyncOkResponse = struct({ unused: u32 }, { littleEndian: true });
async function pipeFileData(locked, file, packetSize, mtime) {
  const abortController = new AbortController();
  file.pipeThrough(new DistributionStream(packetSize, true)).pipeTo(new maybe_consumable_ns_exports.WritableStream({
    write(chunk) {
      return adbSyncWriteRequest(locked, AdbSyncRequestId.Data, chunk);
    }
  }), { signal: abortController.signal }).then(async () => {
    await adbSyncWriteRequest(locked, AdbSyncRequestId.Done, mtime);
    await locked.flush();
  }, NOOP2);
  await adbSyncReadResponse(locked, AdbSyncResponseId.Ok, AdbSyncOkResponse).catch((e) => {
    abortController.abort();
    throw e;
  });
}
async function adbSyncPushV1({ socket, filename, file, type = LinuxFileType.File, permission = 438, mtime = Date.now() / 1e3 | 0, packetSize = ADB_SYNC_MAX_PACKET_SIZE }) {
  const locked = await socket.lock();
  try {
    const mode = type << 12 | permission;
    const pathAndMode = `${filename},${mode.toString()}`;
    await adbSyncWriteRequest(locked, AdbSyncRequestId.Send, pathAndMode);
    await pipeFileData(locked, file, packetSize, mtime);
  } finally {
    locked.release();
  }
}
var AdbSyncSendV2Flags = {
  None: 0,
  Brotli: 1,
  /**
   * 2
   */
  Lz4: 1 << 1,
  /**
   * 4
   */
  Zstd: 1 << 2,
  DryRun: 2147483648
};
var AdbSyncSendV2Request = struct({ id: u32, mode: u32, flags: u32() }, { littleEndian: true });
async function adbSyncPushV2({ socket, filename, file, type = LinuxFileType.File, permission = 438, mtime = Date.now() / 1e3 | 0, packetSize = ADB_SYNC_MAX_PACKET_SIZE, dryRun = false }) {
  const locked = await socket.lock();
  try {
    await adbSyncWriteRequest(locked, AdbSyncRequestId.SendV2, filename);
    const mode = type << 12 | permission;
    let flags = AdbSyncSendV2Flags.None;
    if (dryRun) {
      flags |= AdbSyncSendV2Flags.DryRun;
    }
    await locked.write(AdbSyncSendV2Request.serialize({
      id: AdbSyncRequestId.SendV2,
      mode,
      flags
    }));
    await pipeFileData(locked, file, packetSize, mtime);
  } finally {
    locked.release();
  }
}
function adbSyncPush(options) {
  if (options.v2) {
    return adbSyncPushV2(options);
  }
  if (options.dryRun) {
    throw new Error("dryRun is not supported in v1");
  }
  return adbSyncPushV1(options);
}

// node_modules/@yume-chan/adb/esm/commands/sync/socket.js
var AdbSyncSocketLocked = class {
  #writer;
  #readable;
  #socketLock;
  #writeLock = new AutoResetEvent();
  #combiner;
  get position() {
    return this.#readable.position;
  }
  constructor(writer, readable, bufferSize, lock) {
    this.#writer = writer;
    this.#readable = readable;
    this.#socketLock = lock;
    this.#combiner = new BufferCombiner(bufferSize);
  }
  #write(buffer2) {
    return Consumable.WritableStream.write(this.#writer, buffer2);
  }
  async flush() {
    try {
      await this.#writeLock.wait();
      const buffer2 = this.#combiner.flush();
      if (buffer2) {
        await this.#write(buffer2);
      }
    } finally {
      this.#writeLock.notifyOne();
    }
  }
  async write(data) {
    try {
      await this.#writeLock.wait();
      for (const buffer2 of this.#combiner.push(data)) {
        await this.#write(buffer2);
      }
    } finally {
      this.#writeLock.notifyOne();
    }
  }
  async readExactly(length) {
    await this.flush();
    return await this.#readable.readExactly(length);
  }
  release() {
    this.#combiner.flush();
    this.#socketLock.notifyOne();
  }
  async close() {
    await this.#readable.cancel();
  }
};
var AdbSyncSocket = class {
  #lock = new AutoResetEvent();
  #socket;
  #locked;
  constructor(socket, bufferSize) {
    this.#socket = socket;
    this.#locked = new AdbSyncSocketLocked(socket.writable.getWriter(), new BufferedReadableStream(socket.readable), bufferSize, this.#lock);
  }
  async lock() {
    await this.#lock.wait();
    return this.#locked;
  }
  async close() {
    await this.#locked.close();
    await this.#socket.close();
  }
};

// node_modules/@yume-chan/adb/esm/commands/sync/sync.js
function dirname(path) {
  const end = path.lastIndexOf("/");
  if (end === -1) {
    throw new Error(`Invalid path`);
  }
  if (end === 0) {
    return "/";
  }
  return path.substring(0, end);
}
var AdbSync = class {
  _adb;
  _socket;
  #supportsStat;
  #supportsListV2;
  #fixedPushMkdir;
  #supportsSendReceiveV2;
  #needPushMkdirWorkaround;
  get supportsStat() {
    return this.#supportsStat;
  }
  get supportsListV2() {
    return this.#supportsListV2;
  }
  get fixedPushMkdir() {
    return this.#fixedPushMkdir;
  }
  get supportsSendReceiveV2() {
    return this.#supportsSendReceiveV2;
  }
  get needPushMkdirWorkaround() {
    return this.#needPushMkdirWorkaround;
  }
  constructor(adb2, socket) {
    this._adb = adb2;
    this._socket = new AdbSyncSocket(socket, adb2.maxPayloadSize);
    this.#supportsStat = adb2.canUseFeature(AdbFeature.StatV2);
    this.#supportsListV2 = adb2.canUseFeature(AdbFeature.ListV2);
    this.#fixedPushMkdir = adb2.canUseFeature(AdbFeature.FixedPushMkdir);
    this.#supportsSendReceiveV2 = adb2.canUseFeature(AdbFeature.SendReceiveV2);
    this.#needPushMkdirWorkaround = this._adb.canUseFeature(AdbFeature.ShellV2) && !this.fixedPushMkdir;
  }
  /**
   * Gets information of a file or folder.
   *
   * If `path` points to a symbolic link, the returned information is about the link itself (with `type` being `LinuxFileType.Link`).
   */
  async lstat(path) {
    return await adbSyncLstat(this._socket, path, this.#supportsStat);
  }
  /**
   * Gets the information of a file or folder.
   *
   * If `path` points to a symbolic link, it will be resolved and the returned information is about the target (with `type` being `LinuxFileType.File` or `LinuxFileType.Directory`).
   */
  async stat(path) {
    if (!this.#supportsStat) {
      throw new Error("Not supported");
    }
    return await adbSyncStat(this._socket, path);
  }
  /**
   * Checks if `path` is a directory, or a symbolic link to a directory.
   *
   * This uses `lstat` internally, thus works on all Android versions.
   */
  async isDirectory(path) {
    try {
      await this.lstat(path + "/");
      return true;
    } catch {
      return false;
    }
  }
  opendir(path) {
    return adbSyncOpenDir(this._socket, path, this.supportsListV2);
  }
  async readdir(path) {
    const results = [];
    for await (const entry of this.opendir(path)) {
      results.push(entry);
    }
    return results;
  }
  /**
   * Reads the content of a file on device.
   *
   * @param filename The full path of the file on device to read.
   * @returns A `ReadableStream` that contains the file content.
   */
  read(filename) {
    return adbSyncPull(this._socket, filename);
  }
  /**
   * Writes a file on device. If the file name already exists, it will be overwritten.
   *
   * @param options The content and options of the file to write.
   */
  async write(options) {
    if (this.needPushMkdirWorkaround) {
      await this._adb.subprocess.spawnAndWait([
        "mkdir",
        "-p",
        escapeArg(dirname(options.filename))
      ]);
    }
    await adbSyncPush({
      v2: this.supportsSendReceiveV2,
      socket: this._socket,
      ...options
    });
  }
  lockSocket() {
    return this._socket.lock();
  }
  dispose() {
    return this._socket.close();
  }
};

// node_modules/@yume-chan/adb/esm/commands/tcpip.js
function parsePort(value) {
  if (!value || value === "0") {
    return void 0;
  }
  return Number.parseInt(value, 10);
}
var AdbTcpIpCommand = class extends AdbCommandBase {
  async getListenAddresses() {
    const serviceListenAddresses = await this.adb.getProp("service.adb.listen_addrs");
    const servicePort = await this.adb.getProp("service.adb.tcp.port");
    const persistPort = await this.adb.getProp("persist.adb.tcp.port");
    return {
      serviceListenAddresses: serviceListenAddresses != "" ? serviceListenAddresses.split(",") : [],
      servicePort: parsePort(servicePort),
      persistPort: parsePort(persistPort)
    };
  }
  async setPort(port) {
    if (port <= 0) {
      throw new TypeError(`Invalid port ${port}`);
    }
    const output = await this.adb.createSocketAndWait(`tcpip:${port}`);
    if (output !== `restarting in TCP mode port: ${port}
`) {
      throw new Error(output);
    }
    return output;
  }
  async disable() {
    const output = await this.adb.createSocketAndWait("usb:");
    if (output !== "restarting in USB mode\n") {
      throw new Error(output);
    }
    return output;
  }
};

// node_modules/@yume-chan/adb/esm/adb.js
var Adb = class {
  transport;
  get serial() {
    return this.transport.serial;
  }
  get maxPayloadSize() {
    return this.transport.maxPayloadSize;
  }
  get banner() {
    return this.transport.banner;
  }
  get disconnected() {
    return this.transport.disconnected;
  }
  get clientFeatures() {
    return this.transport.clientFeatures;
  }
  get deviceFeatures() {
    return this.banner.features;
  }
  subprocess;
  power;
  reverse;
  tcpip;
  constructor(transport) {
    this.transport = transport;
    this.subprocess = new AdbSubprocess(this);
    this.power = new AdbPower(this);
    this.reverse = new AdbReverseCommand(this);
    this.tcpip = new AdbTcpIpCommand(this);
  }
  canUseFeature(feature) {
    return this.clientFeatures.includes(feature) && this.deviceFeatures.includes(feature);
  }
  /**
   * Creates a new ADB Socket to the specified service or socket address.
   */
  async createSocket(service) {
    return this.transport.connect(service);
  }
  async createSocketAndWait(service) {
    const socket = await this.createSocket(service);
    return await socket.readable.pipeThrough(new TextDecoderStream()).pipeThrough(new ConcatStringStream());
  }
  async getProp(key) {
    const stdout = await this.subprocess.spawnAndWaitLegacy([
      "getprop",
      key
    ]);
    return stdout.trim();
  }
  async rm(filenames, options) {
    const args = ["rm"];
    if (options?.recursive) {
      args.push("-r");
    }
    if (options?.force) {
      args.push("-f");
    }
    if (Array.isArray(filenames)) {
      for (const filename of filenames) {
        args.push(escapeArg(filename));
      }
    } else {
      args.push(escapeArg(filenames));
    }
    args.push("</dev/null");
    const stdout = await this.subprocess.spawnAndWaitLegacy(args);
    return stdout;
  }
  async sync() {
    const socket = await this.createSocket("sync:");
    return new AdbSync(this, socket);
  }
  async framebuffer() {
    return framebuffer(this);
  }
  async close() {
    await this.transport.close();
  }
};

// node_modules/@yume-chan/adb/esm/banner.js
var AdbBannerKey = {
  Product: "ro.product.name",
  Model: "ro.product.model",
  Device: "ro.product.device",
  Features: "features"
};
var AdbBanner = class _AdbBanner {
  static parse(banner) {
    let product;
    let model;
    let device;
    let features = [];
    const pieces = banner.split("::");
    if (pieces.length > 1) {
      const props = pieces[1];
      for (const prop of props.split(";")) {
        if (!prop) {
          continue;
        }
        const keyValue = prop.split("=");
        if (keyValue.length !== 2) {
          continue;
        }
        const [key, value] = keyValue;
        switch (key) {
          case AdbBannerKey.Product:
            product = value;
            break;
          case AdbBannerKey.Model:
            model = value;
            break;
          case AdbBannerKey.Device:
            device = value;
            break;
          case AdbBannerKey.Features:
            features = value.split(",");
            break;
        }
      }
    }
    return new _AdbBanner(product, model, device, features);
  }
  #product;
  get product() {
    return this.#product;
  }
  #model;
  get model() {
    return this.#model;
  }
  #device;
  get device() {
    return this.#device;
  }
  #features = [];
  get features() {
    return this.#features;
  }
  constructor(product, model, device, features) {
    this.#product = product;
    this.#model = model;
    this.#device = device;
    this.#features = features;
  }
};

// node_modules/@yume-chan/adb/esm/daemon/crypto.js
function getBigUint(array, byteOffset, length) {
  let result = 0n;
  for (let i = byteOffset; i < byteOffset + length; i += 8) {
    result <<= 64n;
    const value = getUint64BigEndian(array, i);
    result |= value;
  }
  return result;
}
function setBigUint(array, byteOffset, length, value, littleEndian) {
  if (littleEndian) {
    while (value > 0n) {
      setInt64LittleEndian(array, byteOffset, value);
      byteOffset += 8;
      value >>= 64n;
    }
  } else {
    let position = byteOffset + length - 8;
    while (value > 0n) {
      setInt64BigEndian(array, position, value);
      position -= 8;
      value >>= 64n;
    }
  }
}
var RsaPrivateKeyNOffset = 38;
var RsaPrivateKeyNLength = 2048 / 8;
var RsaPrivateKeyDOffset = 303;
var RsaPrivateKeyDLength = 2048 / 8;
function rsaParsePrivateKey(key) {
  const n = getBigUint(key, RsaPrivateKeyNOffset, RsaPrivateKeyNLength);
  const d = getBigUint(key, RsaPrivateKeyDOffset, RsaPrivateKeyDLength);
  return [n, d];
}
function nonNegativeMod(m, d) {
  const r = m % d;
  if (r > 0) {
    return r;
  }
  return r + (d > 0 ? d : -d);
}
function modInverse(a, m) {
  a = nonNegativeMod(a, m);
  if (!a || m < 2) {
    return NaN;
  }
  const s = [];
  let b = m;
  while (b) {
    [a, b] = [b, a % b];
    s.push({ a, b });
  }
  if (a !== 1) {
    return NaN;
  }
  let x = 1;
  let y = 0;
  for (let i = s.length - 2; i >= 0; i -= 1) {
    [x, y] = [y, x - y * Math.floor(s[i].a / s[i].b)];
  }
  return nonNegativeMod(y, m);
}
var ModulusLengthInBytes = 2048 / 8;
var ModulusLengthInWords = ModulusLengthInBytes / 4;
function adbGetPublicKeySize() {
  return 4 + 4 + ModulusLengthInBytes + ModulusLengthInBytes + 4;
}
function adbGeneratePublicKey(privateKey, output) {
  let outputType;
  const outputLength = adbGetPublicKeySize();
  if (!output) {
    output = new Uint8Array(outputLength);
    outputType = "Uint8Array";
  } else {
    if (output.length < outputLength) {
      throw new TypeError("output buffer is too small");
    }
    outputType = "number";
  }
  const outputView = new DataView(output.buffer, output.byteOffset, output.length);
  let outputOffset = 0;
  outputView.setUint32(outputOffset, ModulusLengthInWords, true);
  outputOffset += 4;
  const [n] = rsaParsePrivateKey(privateKey);
  const n0inv = -modInverse(Number(n % 2n ** 32n), 2 ** 32);
  outputView.setInt32(outputOffset, n0inv, true);
  outputOffset += 4;
  setBigUint(output, outputOffset, ModulusLengthInBytes, n, true);
  outputOffset += ModulusLengthInBytes;
  const rr = 2n ** 4096n % n;
  setBigUint(output, outputOffset, ModulusLengthInBytes, rr, true);
  outputOffset += ModulusLengthInBytes;
  outputView.setUint32(outputOffset, 65537, true);
  outputOffset += 4;
  if (outputType === "Uint8Array") {
    return output;
  } else {
    return outputLength;
  }
}
function powMod(base, exponent, modulus) {
  if (modulus === 1n) {
    return 0n;
  }
  let r = 1n;
  base = base % modulus;
  while (exponent > 0n) {
    if (BigInt.asUintN(1, exponent) === 1n) {
      r = r * base % modulus;
    }
    base = base * base % modulus;
    exponent >>= 1n;
  }
  return r;
}
var SHA1_DIGEST_LENGTH = 20;
var ASN1_SEQUENCE = 48;
var ASN1_OCTET_STRING = 4;
var ASN1_NULL = 5;
var ASN1_OID = 6;
var SHA1_DIGEST_INFO = new Uint8Array([
  ASN1_SEQUENCE,
  13 + SHA1_DIGEST_LENGTH,
  ASN1_SEQUENCE,
  9,
  // SHA-1 (1 3 14 3 2 26)
  ASN1_OID,
  5,
  1 * 40 + 3,
  14,
  3,
  2,
  26,
  ASN1_NULL,
  0,
  ASN1_OCTET_STRING,
  SHA1_DIGEST_LENGTH
]);
function rsaSign(privateKey, data) {
  const [n, d] = rsaParsePrivateKey(privateKey);
  const padded = new Uint8Array(256);
  let index = 0;
  padded[index] = 0;
  index += 1;
  padded[index] = 1;
  index += 1;
  const fillLength = padded.length - SHA1_DIGEST_INFO.length - data.length - 1;
  while (index < fillLength) {
    padded[index] = 255;
    index += 1;
  }
  padded[index] = 0;
  index += 1;
  padded.set(SHA1_DIGEST_INFO, index);
  index += SHA1_DIGEST_INFO.length;
  padded.set(data, index);
  const signature = powMod(getBigUint(padded, 0, padded.length), d, n);
  setBigUint(padded, 0, padded.length, signature, false);
  return padded;
}

// node_modules/@yume-chan/adb/esm/daemon/packet.js
var AdbCommand = {
  Auth: 1213486401,
  // 'AUTH'
  Close: 1163086915,
  // 'CLSE'
  Connect: 1314410051,
  // 'CNXN'
  Okay: 1497451343,
  // 'OKAY'
  Open: 1313165391,
  // 'OPEN'
  Write: 1163154007
  // 'WRTE'
};
var AdbPacketHeader = struct({
  command: u32,
  arg0: u32,
  arg1: u32,
  payloadLength: u32,
  checksum: u32,
  magic: s32
}, { littleEndian: true });
var AdbPacket = struct(
  /* @__PURE__ */ (() => ({
    ...AdbPacketHeader.fields,
    payload: buffer("payloadLength")
  }))(),
  { littleEndian: true }
);
function calculateChecksum(payload) {
  return payload.reduce((result, item) => result + item, 0);
}
var AdbPacketSerializeStream = class extends TransformStream {
  constructor() {
    const headerBuffer = new Uint8Array(AdbPacketHeader.size);
    super({
      transform: async (chunk, controller) => {
        await chunk.tryConsume(async (chunk2) => {
          const init = chunk2;
          init.payloadLength = init.payload.length;
          AdbPacketHeader.serialize(init, headerBuffer);
          await Consumable.ReadableStream.enqueue(controller, headerBuffer);
          if (init.payloadLength) {
            await Consumable.ReadableStream.enqueue(controller, init.payload);
          }
        });
      }
    });
  }
};

// node_modules/@yume-chan/adb/esm/daemon/auth.js
var AdbAuthType = {
  Token: 1,
  Signature: 2,
  PublicKey: 3
};
var AdbSignatureAuthenticator = async function* (credentialStore, getNextRequest) {
  for await (const key of credentialStore.iterateKeys()) {
    const packet = await getNextRequest();
    if (packet.arg0 !== AdbAuthType.Token) {
      return;
    }
    const signature = rsaSign(key.buffer, packet.payload);
    yield {
      command: AdbCommand.Auth,
      arg0: AdbAuthType.Signature,
      arg1: 0,
      payload: signature
    };
  }
};
var AdbPublicKeyAuthenticator = async function* (credentialStore, getNextRequest) {
  const packet = await getNextRequest();
  if (packet.arg0 !== AdbAuthType.Token) {
    return;
  }
  let privateKey;
  for await (const key of credentialStore.iterateKeys()) {
    privateKey = key;
    break;
  }
  if (!privateKey) {
    privateKey = await credentialStore.generateKey();
  }
  const publicKeyLength = adbGetPublicKeySize();
  const [publicKeyBase64Length] = calculateBase64EncodedLength(publicKeyLength);
  const nameBuffer = privateKey.name?.length ? encodeUtf8(privateKey.name) : EmptyUint8Array;
  const publicKeyBuffer = new Uint8Array(publicKeyBase64Length + (nameBuffer.length ? nameBuffer.length + 1 : 0) + // Space character + name
  1);
  adbGeneratePublicKey(privateKey.buffer, publicKeyBuffer);
  encodeBase64(publicKeyBuffer.subarray(0, publicKeyLength), publicKeyBuffer);
  if (nameBuffer.length) {
    publicKeyBuffer[publicKeyBase64Length] = 32;
    publicKeyBuffer.set(nameBuffer, publicKeyBase64Length + 1);
  }
  yield {
    command: AdbCommand.Auth,
    arg0: AdbAuthType.PublicKey,
    arg1: 0,
    payload: publicKeyBuffer
  };
};
var ADB_DEFAULT_AUTHENTICATORS = [
  AdbSignatureAuthenticator,
  AdbPublicKeyAuthenticator
];
var AdbAuthenticationProcessor = class {
  authenticators;
  #credentialStore;
  #pendingRequest = new PromiseResolver();
  #iterator;
  constructor(authenticators, credentialStore) {
    this.authenticators = authenticators;
    this.#credentialStore = credentialStore;
  }
  #getNextRequest = () => {
    return this.#pendingRequest.promise;
  };
  async *#invokeAuthenticator() {
    for (const authenticator of this.authenticators) {
      for await (const packet of authenticator(this.#credentialStore, this.#getNextRequest)) {
        this.#pendingRequest = new PromiseResolver();
        yield packet;
      }
    }
  }
  async process(packet) {
    if (!this.#iterator) {
      this.#iterator = this.#invokeAuthenticator();
    }
    this.#pendingRequest.resolve(packet);
    const result = await this.#iterator.next();
    if (result.done) {
      throw new Error("No authenticator can handle the request");
    }
    return result.value;
  }
  dispose() {
    void this.#iterator?.return?.();
  }
};

// node_modules/@yume-chan/adb/esm/daemon/socket.js
var AdbDaemonSocketController = class {
  #dispatcher;
  localId;
  remoteId;
  localCreated;
  service;
  #readable;
  #readableController;
  get readable() {
    return this.#readable;
  }
  #writableController;
  writable;
  #closed = false;
  #closedPromise = new PromiseResolver();
  get closed() {
    return this.#closedPromise.promise;
  }
  #socket;
  get socket() {
    return this.#socket;
  }
  #availableWriteBytesChanged;
  /**
   * When delayed ack is disabled, returns `Infinity` if the socket is ready to write
   * (exactly one packet can be written no matter how large it is), or `-1` if the socket
   * is waiting for ack message.
   *
   * When delayed ack is enabled, returns a non-negative finite number indicates the number of
   * bytes that can be written to the socket before waiting for ack message.
   */
  #availableWriteBytes = 0;
  constructor(options) {
    this.#dispatcher = options.dispatcher;
    this.localId = options.localId;
    this.remoteId = options.remoteId;
    this.localCreated = options.localCreated;
    this.service = options.service;
    this.#readable = new PushReadableStream((controller) => {
      this.#readableController = controller;
    });
    this.writable = new maybe_consumable_ns_exports.WritableStream({
      start: (controller) => {
        this.#writableController = controller;
        controller.signal.addEventListener("abort", () => {
          this.#availableWriteBytesChanged?.reject(controller.signal.reason);
        });
      },
      write: async (data) => {
        const size = data.length;
        const chunkSize = this.#dispatcher.options.maxPayloadSize;
        for (let start = 0, end = chunkSize; start < size; start = end, end += chunkSize) {
          const chunk = data.subarray(start, end);
          await this.#writeChunk(chunk);
        }
      }
    });
    this.#socket = new AdbDaemonSocket(this);
    this.#availableWriteBytes = options.availableWriteBytes;
  }
  async #writeChunk(data) {
    const length = data.length;
    while (this.#availableWriteBytes < length) {
      const resolver = new PromiseResolver();
      this.#availableWriteBytesChanged = resolver;
      await resolver.promise;
    }
    if (this.#availableWriteBytes === Infinity) {
      this.#availableWriteBytes = -1;
    } else {
      this.#availableWriteBytes -= length;
    }
    await this.#dispatcher.sendPacket(AdbCommand.Write, this.localId, this.remoteId, data);
  }
  async enqueue(data) {
    await this.#readableController.enqueue(data);
  }
  ack(bytes) {
    this.#availableWriteBytes += bytes;
    this.#availableWriteBytesChanged?.resolve();
  }
  async close() {
    if (this.#closed) {
      return;
    }
    this.#closed = true;
    this.#availableWriteBytesChanged?.reject(new Error("Socket closed"));
    try {
      this.#writableController.error(new Error("Socket closed"));
    } catch {
    }
    await this.#dispatcher.sendPacket(AdbCommand.Close, this.localId, this.remoteId, EmptyUint8Array);
  }
  dispose() {
    this.#readableController.close();
    this.#closedPromise.resolve();
  }
};
var AdbDaemonSocket = class {
  #controller;
  get localId() {
    return this.#controller.localId;
  }
  get remoteId() {
    return this.#controller.remoteId;
  }
  get localCreated() {
    return this.#controller.localCreated;
  }
  get service() {
    return this.#controller.service;
  }
  get readable() {
    return this.#controller.readable;
  }
  get writable() {
    return this.#controller.writable;
  }
  get closed() {
    return this.#controller.closed;
  }
  constructor(controller) {
    this.#controller = controller;
  }
  close() {
    return this.#controller.close();
  }
};

// node_modules/@yume-chan/adb/esm/daemon/dispatcher.js
var AdbPacketDispatcher = class {
  // ADB socket id starts from 1
  // (0 means open failed)
  #initializers = new AsyncOperationManager(1);
  /**
   * Socket local ID to the socket controller.
   */
  #sockets = /* @__PURE__ */ new Map();
  #writer;
  options;
  #closed = false;
  #disconnected = new PromiseResolver();
  get disconnected() {
    return this.#disconnected.promise;
  }
  #incomingSocketHandlers = /* @__PURE__ */ new Map();
  #readAbortController = new AbortController();
  constructor(connection2, options) {
    this.options = options;
    if (this.options.initialDelayedAckBytes < 0) {
      this.options.initialDelayedAckBytes = 0;
    }
    connection2.readable.pipeTo(new WritableStream({
      write: async (packet) => {
        switch (packet.command) {
          case AdbCommand.Close:
            await this.#handleClose(packet);
            break;
          case AdbCommand.Okay:
            this.#handleOkay(packet);
            break;
          case AdbCommand.Open:
            await this.#handleOpen(packet);
            break;
          case AdbCommand.Write:
            await this.#handleWrite(packet);
            break;
          default:
            throw new Error(`Unknown command: ${packet.command.toString(16)}`);
        }
      }
    }), {
      preventCancel: options.preserveConnection ?? false,
      signal: this.#readAbortController.signal
    }).then(() => {
      this.#dispose();
    }, (e) => {
      if (!this.#closed) {
        this.#disconnected.reject(e);
      }
      this.#dispose();
    });
    this.#writer = connection2.writable.getWriter();
  }
  async #handleClose(packet) {
    if (packet.arg0 === 0 && this.#initializers.reject(packet.arg1, new Error("Socket open failed"))) {
      return;
    }
    const socket = this.#sockets.get(packet.arg1);
    if (socket) {
      await socket.close();
      socket.dispose();
      this.#sockets.delete(packet.arg1);
      return;
    }
  }
  #handleOkay(packet) {
    let ackBytes;
    if (this.options.initialDelayedAckBytes !== 0) {
      if (packet.payload.length !== 4) {
        throw new Error("Invalid OKAY packet. Payload size should be 4");
      }
      ackBytes = getUint32LittleEndian(packet.payload, 0);
    } else {
      if (packet.payload.length !== 0) {
        throw new Error("Invalid OKAY packet. Payload size should be 0");
      }
      ackBytes = Infinity;
    }
    if (this.#initializers.resolve(packet.arg1, {
      remoteId: packet.arg0,
      availableWriteBytes: ackBytes
    })) {
      return;
    }
    const socket = this.#sockets.get(packet.arg1);
    if (socket) {
      socket.ack(ackBytes);
      return;
    }
    void this.sendPacket(AdbCommand.Close, packet.arg1, packet.arg0, EmptyUint8Array);
  }
  #sendOkay(localId, remoteId, ackBytes) {
    let payload;
    if (this.options.initialDelayedAckBytes !== 0) {
      payload = new Uint8Array(4);
      setUint32LittleEndian(payload, 0, ackBytes);
    } else {
      payload = EmptyUint8Array;
    }
    return this.sendPacket(AdbCommand.Okay, localId, remoteId, payload);
  }
  async #handleOpen(packet) {
    const [localId] = this.#initializers.add();
    this.#initializers.resolve(localId, void 0);
    const remoteId = packet.arg0;
    let availableWriteBytes = packet.arg1;
    let service = decodeUtf8(packet.payload);
    if (service.endsWith("\0")) {
      service = service.substring(0, service.length - 1);
    }
    if (this.options.initialDelayedAckBytes === 0) {
      if (availableWriteBytes !== 0) {
        throw new Error("Invalid OPEN packet. arg1 should be 0");
      }
      availableWriteBytes = Infinity;
    } else {
      if (availableWriteBytes === 0) {
        throw new Error("Invalid OPEN packet. arg1 should be greater than 0");
      }
    }
    const handler = this.#incomingSocketHandlers.get(service);
    if (!handler) {
      await this.sendPacket(AdbCommand.Close, 0, remoteId, EmptyUint8Array);
      return;
    }
    const controller = new AdbDaemonSocketController({
      dispatcher: this,
      localId,
      remoteId,
      localCreated: false,
      service,
      availableWriteBytes
    });
    try {
      await handler(controller.socket);
      this.#sockets.set(localId, controller);
      await this.#sendOkay(localId, remoteId, this.options.initialDelayedAckBytes);
    } catch {
      await this.sendPacket(AdbCommand.Close, 0, remoteId, EmptyUint8Array);
    }
  }
  async #handleWrite(packet) {
    const socket = this.#sockets.get(packet.arg1);
    if (!socket) {
      throw new Error(`Unknown local socket id: ${packet.arg1}`);
    }
    let handled = false;
    const promises = [
      (async () => {
        await socket.enqueue(packet.payload);
        await this.#sendOkay(packet.arg1, packet.arg0, packet.payload.length);
        handled = true;
      })()
    ];
    if (this.options.readTimeLimit) {
      promises.push((async () => {
        await delay(this.options.readTimeLimit);
        if (!handled) {
          throw new Error(`readable of \`${socket.service}\` has stalled for ${this.options.readTimeLimit} milliseconds`);
        }
      })());
    }
    await Promise.race(promises);
  }
  async createSocket(service) {
    if (this.options.appendNullToServiceString) {
      service += "\0";
    }
    const [localId, initializer] = this.#initializers.add();
    await this.sendPacket(AdbCommand.Open, localId, this.options.initialDelayedAckBytes, service);
    const { remoteId, availableWriteBytes } = await initializer;
    const controller = new AdbDaemonSocketController({
      dispatcher: this,
      localId,
      remoteId,
      localCreated: true,
      service,
      availableWriteBytes
    });
    this.#sockets.set(localId, controller);
    return controller.socket;
  }
  addReverseTunnel(service, handler) {
    this.#incomingSocketHandlers.set(service, handler);
  }
  removeReverseTunnel(address) {
    this.#incomingSocketHandlers.delete(address);
  }
  clearReverseTunnels() {
    this.#incomingSocketHandlers.clear();
  }
  async sendPacket(command, arg0, arg1, payload) {
    if (typeof payload === "string") {
      payload = encodeUtf8(payload);
    }
    if (payload.length > this.options.maxPayloadSize) {
      throw new TypeError("payload too large");
    }
    await Consumable.WritableStream.write(this.#writer, {
      command,
      arg0,
      arg1,
      payload,
      checksum: this.options.calculateChecksum ? calculateChecksum(payload) : 0,
      magic: command ^ 4294967295
    });
  }
  async close() {
    await Promise.all(Array.from(this.#sockets.values(), (socket) => socket.close()));
    this.#closed = true;
    this.#readAbortController.abort();
    if (this.options.preserveConnection) {
      this.#writer.releaseLock();
    } else {
      await this.#writer.close();
    }
  }
  #dispose() {
    for (const socket of this.#sockets.values()) {
      socket.dispose();
    }
    this.#disconnected.resolve();
  }
};

// node_modules/@yume-chan/adb/esm/daemon/transport.js
var ADB_DAEMON_VERSION_OMIT_CHECKSUM = 16777217;
var ADB_DAEMON_DEFAULT_FEATURES = /* @__PURE__ */ (() => [
  AdbFeature.ShellV2,
  AdbFeature.Cmd,
  AdbFeature.StatV2,
  AdbFeature.ListV2,
  AdbFeature.FixedPushMkdir,
  "apex",
  AdbFeature.Abb,
  // only tells the client the symlink timestamp issue in `adb push --sync` has been fixed.
  // No special handling required.
  "fixed_push_symlink_timestamp",
  AdbFeature.AbbExec,
  "remount_shell",
  "track_app",
  AdbFeature.SendReceiveV2,
  "sendrecv_v2_brotli",
  "sendrecv_v2_lz4",
  "sendrecv_v2_zstd",
  "sendrecv_v2_dry_run_send",
  AdbFeature.DelayedAck
])();
var ADB_DAEMON_DEFAULT_INITIAL_PAYLOAD_SIZE = 32 * 1024 * 1024;
var AdbDaemonTransport = class _AdbDaemonTransport {
  /**
   * Authenticate with the ADB Daemon and create a new transport.
   */
  static async authenticate({ serial, connection: connection2, credentialStore, authenticators = ADB_DEFAULT_AUTHENTICATORS, features = ADB_DAEMON_DEFAULT_FEATURES, initialDelayedAckBytes = ADB_DAEMON_DEFAULT_INITIAL_PAYLOAD_SIZE, ...options }) {
    let version = 16777217;
    let maxPayloadSize = 1024 * 1024;
    const resolver = new PromiseResolver();
    const authProcessor = new AdbAuthenticationProcessor(authenticators, credentialStore);
    const abortController = new AbortController();
    const pipe = connection2.readable.pipeTo(new WritableStream({
      async write(packet) {
        switch (packet.command) {
          case AdbCommand.Connect:
            version = Math.min(version, packet.arg0);
            maxPayloadSize = Math.min(maxPayloadSize, packet.arg1);
            resolver.resolve(decodeUtf8(packet.payload));
            break;
          case AdbCommand.Auth: {
            const response = await authProcessor.process(packet);
            await sendPacket(response);
            break;
          }
          default:
            break;
        }
      }
    }), {
      // Don't cancel the source ReadableStream on AbortSignal abort.
      preventCancel: true,
      signal: abortController.signal
    }).then(() => {
      resolver.reject(new Error("Connection closed unexpectedly"));
    }, (e) => {
      resolver.reject(e);
    });
    const writer = connection2.writable.getWriter();
    async function sendPacket(init) {
      init.checksum = calculateChecksum(init.payload);
      init.magic = init.command ^ 4294967295;
      await Consumable.WritableStream.write(writer, init);
    }
    const actualFeatures = features.slice();
    if (initialDelayedAckBytes <= 0) {
      const index = features.indexOf(AdbFeature.DelayedAck);
      if (index !== -1) {
        actualFeatures.splice(index, 1);
      }
    }
    let banner;
    try {
      await sendPacket({
        command: AdbCommand.Connect,
        arg0: version,
        arg1: maxPayloadSize,
        // The terminating `;` is required in formal definition
        // But ADB daemon (all versions) can still work without it
        payload: encodeUtf8(`host::features=${actualFeatures.join(",")}`)
      });
      banner = await resolver.promise;
    } finally {
      abortController.abort();
      writer.releaseLock();
      await pipe;
    }
    return new _AdbDaemonTransport({
      serial,
      connection: connection2,
      version,
      maxPayloadSize,
      banner,
      features: actualFeatures,
      initialDelayedAckBytes,
      ...options
    });
  }
  #connection;
  get connection() {
    return this.#connection;
  }
  #dispatcher;
  #serial;
  get serial() {
    return this.#serial;
  }
  #protocolVersion;
  get protocolVersion() {
    return this.#protocolVersion;
  }
  get maxPayloadSize() {
    return this.#dispatcher.options.maxPayloadSize;
  }
  #banner;
  get banner() {
    return this.#banner;
  }
  get disconnected() {
    return this.#dispatcher.disconnected;
  }
  #clientFeatures;
  get clientFeatures() {
    return this.#clientFeatures;
  }
  constructor({ serial, connection: connection2, version, banner, features = ADB_DAEMON_DEFAULT_FEATURES, initialDelayedAckBytes, ...options }) {
    this.#serial = serial;
    this.#connection = connection2;
    this.#banner = AdbBanner.parse(banner);
    this.#clientFeatures = features;
    if (features.includes(AdbFeature.DelayedAck)) {
      if (initialDelayedAckBytes <= 0) {
        throw new TypeError("`initialDelayedAckBytes` must be greater than 0 when DelayedAck feature is enabled.");
      }
      if (!this.#banner.features.includes(AdbFeature.DelayedAck)) {
        initialDelayedAckBytes = 0;
      }
    } else {
      initialDelayedAckBytes = 0;
    }
    let calculateChecksum2;
    let appendNullToServiceString;
    if (version >= ADB_DAEMON_VERSION_OMIT_CHECKSUM) {
      calculateChecksum2 = false;
      appendNullToServiceString = false;
    } else {
      calculateChecksum2 = true;
      appendNullToServiceString = true;
    }
    this.#dispatcher = new AdbPacketDispatcher(connection2, {
      calculateChecksum: calculateChecksum2,
      appendNullToServiceString,
      initialDelayedAckBytes,
      ...options
    });
    this.#protocolVersion = version;
  }
  connect(service) {
    return this.#dispatcher.createSocket(service);
  }
  addReverseTunnel(handler, address) {
    if (!address) {
      const id = Math.random().toString().substring(2);
      address = `localabstract:reverse_${id}`;
    }
    this.#dispatcher.addReverseTunnel(address, handler);
    return address;
  }
  removeReverseTunnel(address) {
    this.#dispatcher.removeReverseTunnel(address);
  }
  clearReverseTunnels() {
    this.#dispatcher.clearReverseTunnels();
  }
  close() {
    return this.#dispatcher.close();
  }
};

// node_modules/@yume-chan/adb-daemon-webusb/esm/utils.js
function isErrorName(e, name) {
  return typeof e === "object" && e !== null && "name" in e && e.name === name;
}
function isUsbInterfaceFilter(filter) {
  return filter.classCode !== void 0 && filter.subclassCode !== void 0 && filter.protocolCode !== void 0;
}
function matchUsbInterfaceFilter(alternate, filter) {
  return alternate.interfaceClass === filter.classCode && alternate.interfaceSubclass === filter.subclassCode && alternate.interfaceProtocol === filter.protocolCode;
}
function findUsbInterface(device, filter) {
  for (const configuration of device.configurations) {
    for (const interface_ of configuration.interfaces) {
      for (const alternate of interface_.alternates) {
        if (matchUsbInterfaceFilter(alternate, filter)) {
          return { configuration, interface_, alternate };
        }
      }
    }
  }
  return void 0;
}
function padNumber(value) {
  return value.toString(16).padStart(4, "0");
}
function getSerialNumber(device) {
  if (device.serialNumber) {
    return device.serialNumber;
  }
  return padNumber(device.vendorId) + "x" + padNumber(device.productId);
}
function findUsbEndpoints(endpoints) {
  if (endpoints.length === 0) {
    throw new TypeError("No endpoints given");
  }
  let inEndpoint;
  let outEndpoint;
  for (const endpoint of endpoints) {
    switch (endpoint.direction) {
      case "in":
        inEndpoint = endpoint;
        if (outEndpoint) {
          return { inEndpoint, outEndpoint };
        }
        break;
      case "out":
        outEndpoint = endpoint;
        if (inEndpoint) {
          return { inEndpoint, outEndpoint };
        }
        break;
    }
  }
  if (!inEndpoint) {
    throw new TypeError("No input endpoint found.");
  }
  if (!outEndpoint) {
    throw new TypeError("No output endpoint found.");
  }
  throw new Error("unreachable");
}
function matchFilter(device, filter) {
  if (filter.vendorId !== void 0 && device.vendorId !== filter.vendorId) {
    return false;
  }
  if (filter.productId !== void 0 && device.productId !== filter.productId) {
    return false;
  }
  if (filter.serialNumber !== void 0 && getSerialNumber(device) !== filter.serialNumber) {
    return false;
  }
  if (isUsbInterfaceFilter(filter)) {
    return findUsbInterface(device, filter) || false;
  }
  return true;
}
function matchFilters(device, filters, exclusionFilters) {
  if (exclusionFilters && exclusionFilters.length > 0) {
    if (matchFilters(device, exclusionFilters)) {
      return false;
    }
  }
  for (const filter of filters) {
    const result = matchFilter(device, filter);
    if (result) {
      return result;
    }
  }
  return false;
}

// node_modules/@yume-chan/adb-daemon-webusb/esm/device.js
var AdbDefaultInterfaceFilter = {
  classCode: 255,
  subclassCode: 66,
  protocolCode: 1
};
function mergeDefaultAdbInterfaceFilter(filters) {
  if (!filters || filters.length === 0) {
    return [AdbDefaultInterfaceFilter];
  } else {
    return filters.map((filter) => ({
      ...filter,
      classCode: filter.classCode ?? AdbDefaultInterfaceFilter.classCode,
      subclassCode: filter.subclassCode ?? AdbDefaultInterfaceFilter.subclassCode,
      protocolCode: filter.protocolCode ?? AdbDefaultInterfaceFilter.protocolCode
    }));
  }
}
var Uint8ArrayExactReadable = class {
  #data;
  #position;
  get position() {
    return this.#position;
  }
  constructor(data) {
    this.#data = data;
    this.#position = 0;
  }
  readExactly(length) {
    const result = this.#data.subarray(this.#position, this.#position + length);
    this.#position += length;
    return result;
  }
};
var AdbDaemonWebUsbConnection = class {
  #device;
  get device() {
    return this.#device;
  }
  #inEndpoint;
  get inEndpoint() {
    return this.#inEndpoint;
  }
  #outEndpoint;
  get outEndpoint() {
    return this.#outEndpoint;
  }
  #readable;
  get readable() {
    return this.#readable;
  }
  #writable;
  get writable() {
    return this.#writable;
  }
  constructor(device, inEndpoint, outEndpoint, usbManager) {
    this.#device = device;
    this.#inEndpoint = inEndpoint;
    this.#outEndpoint = outEndpoint;
    let closed2 = false;
    const duplex = new DuplexStreamFactory({
      close: async () => {
        try {
          closed2 = true;
          await device.raw.close();
        } catch {
        }
      },
      dispose: () => {
        closed2 = true;
        usbManager.removeEventListener("disconnect", handleUsbDisconnect);
      }
    });
    function handleUsbDisconnect(e) {
      if (e.device === device.raw) {
        duplex.dispose().catch(unreachable);
      }
    }
    usbManager.addEventListener("disconnect", handleUsbDisconnect);
    this.#readable = duplex.wrapReadable(new ReadableStream2({
      pull: async (controller) => {
        const packet = await this.#transferIn();
        if (packet) {
          controller.enqueue(packet);
        } else {
          controller.close();
        }
      }
    }, { highWaterMark: 0 }));
    const zeroMask = outEndpoint.packetSize - 1;
    this.#writable = pipeFrom(duplex.createWritable(new maybe_consumable_ns_exports.WritableStream({
      write: async (chunk) => {
        try {
          await device.raw.transferOut(outEndpoint.endpointNumber, chunk);
          if (zeroMask && (chunk.length & zeroMask) === 0) {
            await device.raw.transferOut(outEndpoint.endpointNumber, EmptyUint8Array);
          }
        } catch (e) {
          if (closed2) {
            return;
          }
          throw e;
        }
      }
    })), new AdbPacketSerializeStream());
  }
  async #transferIn() {
    try {
      while (true) {
        const result = await this.#device.raw.transferIn(this.#inEndpoint.endpointNumber, this.#inEndpoint.packetSize);
        if (result.data.byteLength !== 24) {
          continue;
        }
        const buffer2 = new Uint8Array(result.data.buffer);
        const stream = new Uint8ArrayExactReadable(buffer2);
        const packet = AdbPacketHeader.deserialize(stream);
        if (packet.magic !== (packet.command ^ 4294967295)) {
          continue;
        }
        if (packet.payloadLength !== 0) {
          const result2 = await this.#device.raw.transferIn(this.#inEndpoint.endpointNumber, packet.payloadLength);
          packet.payload = new Uint8Array(result2.data.buffer);
        } else {
          packet.payload = EmptyUint8Array;
        }
        return packet;
      }
    } catch (e) {
      if (isErrorName(e, "NetworkError")) {
        await new Promise((resolve) => {
          setTimeout(() => {
            resolve();
          }, 100);
        });
        if (closed) {
          return void 0;
        }
      }
      throw e;
    }
  }
};
var AdbDaemonWebUsbDevice = class _AdbDaemonWebUsbDevice {
  #interface;
  #usbManager;
  #raw;
  get raw() {
    return this.#raw;
  }
  #serial;
  get serial() {
    return this.#serial;
  }
  get name() {
    return this.#raw.productName;
  }
  /**
   * Create a new instance of `AdbDaemonWebUsbConnection` using a specified `USBDevice` instance
   *
   * @param device The `USBDevice` instance obtained elsewhere.
   * @param filters The filters to use when searching for ADB interface. Defaults to {@link ADB_DEFAULT_DEVICE_FILTER}.
   */
  constructor(device, interface_, usbManager) {
    this.#raw = device;
    this.#serial = getSerialNumber(device);
    this.#interface = interface_;
    this.#usbManager = usbManager;
  }
  async #claimInterface() {
    if (!this.#raw.opened) {
      await this.#raw.open();
    }
    const { configuration, interface_, alternate } = this.#interface;
    if (this.#raw.configuration?.configurationValue !== configuration.configurationValue) {
      await this.#raw.selectConfiguration(configuration.configurationValue);
    }
    if (!interface_.claimed) {
      try {
        await this.#raw.claimInterface(interface_.interfaceNumber);
      } catch (e) {
        if (isErrorName(e, "NetworkError")) {
          throw new _AdbDaemonWebUsbDevice.DeviceBusyError(e);
        }
        throw e;
      }
    }
    if (interface_.alternate.alternateSetting !== alternate.alternateSetting) {
      await this.#raw.selectAlternateInterface(interface_.interfaceNumber, alternate.alternateSetting);
    }
    return findUsbEndpoints(alternate.endpoints);
  }
  /**
   * Open the device and create a new connection to the ADB Daemon.
   */
  async connect() {
    const { inEndpoint, outEndpoint } = await this.#claimInterface();
    return new AdbDaemonWebUsbConnection(this, inEndpoint, outEndpoint, this.#usbManager);
  }
};
(function(AdbDaemonWebUsbDevice2) {
  class DeviceBusyError extends Error {
    constructor(cause) {
      super("The device is already in used by another program", {
        cause
      });
    }
  }
  AdbDaemonWebUsbDevice2.DeviceBusyError = DeviceBusyError;
})(AdbDaemonWebUsbDevice || (AdbDaemonWebUsbDevice = {}));

// node_modules/@yume-chan/adb-daemon-webusb/esm/observer.js
var AdbDaemonWebUsbDeviceObserver = class {
  #filters;
  #exclusionFilters;
  #usbManager;
  #onDeviceAdd = new EventEmitter();
  onDeviceAdd = this.#onDeviceAdd.event;
  #onDeviceRemove = new EventEmitter();
  onDeviceRemove = this.#onDeviceRemove.event;
  #onListChange = new EventEmitter();
  onListChange = this.#onListChange.event;
  current = [];
  constructor(usb, options = {}) {
    this.#filters = mergeDefaultAdbInterfaceFilter(options.filters);
    this.#exclusionFilters = options.exclusionFilters;
    this.#usbManager = usb;
    this.#usbManager.addEventListener("connect", this.#handleConnect);
    this.#usbManager.addEventListener("disconnect", this.#handleDisconnect);
  }
  #handleConnect = (e) => {
    const interface_ = matchFilters(e.device, this.#filters, this.#exclusionFilters);
    if (!interface_) {
      return;
    }
    const device = new AdbDaemonWebUsbDevice(e.device, interface_, this.#usbManager);
    this.#onDeviceAdd.fire([device]);
    this.current.push(device);
    this.#onListChange.fire(this.current);
  };
  #handleDisconnect = (e) => {
    const index = this.current.findIndex((device) => device.raw === e.device);
    if (index !== -1) {
      const device = this.current[index];
      this.#onDeviceRemove.fire([device]);
      this.current[index] = this.current[this.current.length - 1];
      this.current.length -= 1;
      this.#onListChange.fire(this.current);
    }
  };
  stop() {
    this.#usbManager.removeEventListener("connect", this.#handleConnect);
    this.#usbManager.removeEventListener("disconnect", this.#handleDisconnect);
    this.#onDeviceAdd.dispose();
    this.#onDeviceRemove.dispose();
    this.#onListChange.dispose();
  }
};

// node_modules/@yume-chan/adb-daemon-webusb/esm/manager.js
var AdbDaemonWebUsbDeviceManager = class _AdbDaemonWebUsbDeviceManager {
  /**
   * Gets the instance of {@link AdbDaemonWebUsbDeviceManager} using browser WebUSB implementation.
   *
   * May be `undefined` if current runtime does not support WebUSB.
   */
  static BROWSER = typeof globalThis.navigator !== "undefined" && !!globalThis.navigator.usb ? new _AdbDaemonWebUsbDeviceManager(globalThis.navigator.usb) : void 0;
  #usbManager;
  /**
   * Create a new instance of {@link AdbDaemonWebUsbDeviceManager} using the specified WebUSB implementation.
   * @param usbManager A WebUSB compatible interface.
   */
  constructor(usbManager) {
    this.#usbManager = usbManager;
  }
  /**
   * Call `USB#requestDevice()` to prompt the user to select a device.
   */
  async requestDevice(options = {}) {
    const filters = mergeDefaultAdbInterfaceFilter(options.filters);
    try {
      const device = await this.#usbManager.requestDevice({
        filters,
        exclusionFilters: options.exclusionFilters
      });
      const interface_ = matchFilters(device, filters, options.exclusionFilters);
      if (!interface_) {
        return void 0;
      }
      return new AdbDaemonWebUsbDevice(device, interface_, this.#usbManager);
    } catch (e) {
      if (isErrorName(e, "NotFoundError")) {
        return void 0;
      }
      throw e;
    }
  }
  /**
   * Get all connected and requested devices that match the specified filters.
   */
  async getDevices(options = {}) {
    const filters = mergeDefaultAdbInterfaceFilter(options.filters);
    const devices = await this.#usbManager.getDevices();
    const result = [];
    for (const device of devices) {
      const interface_ = matchFilters(device, filters, options.exclusionFilters);
      if (interface_) {
        result.push(new AdbDaemonWebUsbDevice(device, interface_, this.#usbManager));
      }
    }
    return result;
  }
  trackDevices(options = {}) {
    return new AdbDaemonWebUsbDeviceObserver(this.#usbManager, options);
  }
};

// node_modules/@yume-chan/adb-credential-web/esm/index.js
function openDatabase() {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open("Tango", 1);
    request.onerror = () => {
      reject(request.error);
    };
    request.onupgradeneeded = () => {
      const db = request.result;
      db.createObjectStore("Authentication", { autoIncrement: true });
    };
    request.onsuccess = () => {
      const db = request.result;
      resolve(db);
    };
  });
}
async function saveKey(key) {
  const db = await openDatabase();
  return new Promise((resolve, reject) => {
    const transaction = db.transaction("Authentication", "readwrite");
    const store = transaction.objectStore("Authentication");
    const putRequest = store.add(key);
    putRequest.onerror = () => {
      reject(putRequest.error);
    };
    putRequest.onsuccess = () => {
      resolve();
    };
    transaction.onerror = () => {
      reject(transaction.error);
    };
    transaction.oncomplete = () => {
      db.close();
    };
  });
}
async function getAllKeys() {
  const db = await openDatabase();
  return new Promise((resolve, reject) => {
    const transaction = db.transaction("Authentication", "readonly");
    const store = transaction.objectStore("Authentication");
    const getRequest = store.getAll();
    getRequest.onerror = () => {
      reject(getRequest.error);
    };
    getRequest.onsuccess = () => {
      resolve(getRequest.result);
    };
    transaction.onerror = () => {
      reject(transaction.error);
    };
    transaction.oncomplete = () => {
      db.close();
    };
  });
}
var AdbWebCredentialStore = class {
  #appName;
  constructor(appName = "Tango") {
    this.#appName = appName;
  }
  /**
   * Generates a RSA private key and store it into LocalStorage.
   *
   * Calling this method multiple times will overwrite the previous key.
   *
   * @returns The private key in PKCS #8 format.
   */
  async generateKey() {
    const { privateKey: cryptoKey } = await crypto.subtle.generateKey({
      name: "RSASSA-PKCS1-v1_5",
      modulusLength: 2048,
      // 65537
      publicExponent: new Uint8Array([1, 0, 1]),
      hash: "SHA-1"
    }, true, ["sign", "verify"]);
    const privateKey = new Uint8Array(await crypto.subtle.exportKey("pkcs8", cryptoKey));
    await saveKey(privateKey);
    return {
      buffer: privateKey,
      name: `${this.#appName}@${globalThis.location.hostname}`
    };
  }
  /**
   * Yields the stored RSA private key.
   *
   * This method returns a generator, so `for await...of...` loop should be used to read the key.
   */
  async *iterateKeys() {
    for (const key of await getAllKeys()) {
      yield {
        buffer: key,
        name: `${this.#appName}@${globalThis.location.hostname}`
      };
    }
  }
};

// assets/install.js
var PACKAGE_ID = "ru.jeplay.app";
var VERSION_URL = new URL("../version.txt", import.meta.url).href;
var DOWNLOAD_BASE = new URL("../download/", import.meta.url).href;
var HELPER_URL = new URL("./localinstall.apk", import.meta.url).href;
var REMOTE_TARGET = "/data/local/tmp/jeplay-install-target.apk";
var REMOTE_HELPER = "/data/local/tmp/jeplay-localinstall.apk";
var SOFTAP_IMPORT_PATH = "/data/local/tmp/ru.jeplay.app-softap-import";
var APCONFIG_READ_HEADER = "DIPLAY_HOTSPOT_APCONFIG_READ_V1|";
var HOTSPOT_HELPER_CLASS = "com.shilapi.xcertplay.network.CarHotspotStartMain";
var CREDENTIAL_STORE_NAME = "jeplay-webadb";
var V2_K = 566789n;
var RUNTIME_GRANTS = [
  "android.permission.ACCESS_FINE_LOCATION",
  "android.permission.ACCESS_COARSE_LOCATION",
  "android.permission.RECORD_AUDIO",
  "android.permission.BLUETOOTH_CONNECT",
  "android.permission.BLUETOOTH_SCAN",
  "android.permission.NEARBY_WIFI_DEVICES",
  "android.permission.POST_NOTIFICATIONS",
  "android.permission.BLUETOOTH",
  "android.permission.BLUETOOTH_ADMIN"
];
var APPOPS = [
  "SYSTEM_ALERT_WINDOW",
  "GET_USAGE_STATS",
  "WRITE_SETTINGS",
  "ACTIVATE_VPN",
  "REQUEST_INSTALL_PACKAGES",
  "MANAGE_EXTERNAL_STORAGE",
  "LEGACY_STORAGE",
  "SCHEDULE_EXACT_ALARM",
  "ACCESS_NOTIFICATIONS"
];
var $ = (id) => document.getElementById(id);
function trackGoal(target, params) {
  if (typeof window.jeplayYmGoal === "function") {
    window.jeplayYmGoal(target, params);
  }
}
var actionBtn = $("action-btn");
var disconnectBtn = $("disconnect-btn");
var statusPill = $("status-pill");
var logEl = $("log");
var finalBanner = $("final-banner");
var existingPanel = $("existing-panel");
var existingVersionEl = $("existing-version");
var reinstallBtn = $("reinstall-btn");
var softapPanel = $("softap-panel");
var softapSsidEl = $("softap-ssid");
var softapPassEl = $("softap-pass");
var passwordOut = $("password-out");
var adb = null;
var connection = null;
var busy = false;
var installedPackage = null;
function setStatus(text, kind = "") {
  statusPill.textContent = text;
  statusPill.className = `status-pill${kind ? ` ${kind}` : ""}`;
}
function log(line, kind = "") {
  const span = document.createElement("span");
  if (kind) span.className = kind;
  span.textContent = `${line}
`;
  if (logEl.textContent === "\u041E\u0436\u0438\u0434\u0430\u043D\u0438\u0435 \u043F\u043E\u0434\u043A\u043B\u044E\u0447\u0435\u043D\u0438\u044F\u2026") logEl.textContent = "";
  logEl.appendChild(span);
  logEl.scrollTop = logEl.scrollHeight;
}
function showFinal(ok, message) {
  finalBanner.className = `final-banner show ${ok ? "ok" : "err"}`;
  finalBanner.textContent = message;
}
function setConnectedUi(on) {
  if (!on) {
    actionBtn.textContent = "Connect";
  } else if (installedPackage) {
    actionBtn.textContent = "\u041E\u0431\u043D\u043E\u0432\u0438\u0442\u044C \u043F\u043E\u0432\u0435\u0440\u0445";
  } else {
    actionBtn.textContent = "\u0423\u0441\u0442\u0430\u043D\u043E\u0432\u0438\u0442\u044C";
  }
  actionBtn.disabled = false;
  disconnectBtn.disabled = !on;
  if (reinstallBtn) reinstallBtn.disabled = !on || busy;
}
function hideExistingPanel() {
  installedPackage = null;
  if (existingPanel) existingPanel.hidden = true;
  if (existingVersionEl) existingVersionEl.textContent = "";
}
function showExistingPanel(info) {
  installedPackage = info;
  if (!existingPanel || !existingVersionEl) return;
  existingVersionEl.textContent = info.versionName || info.versionCode || "\u0443\u0441\u0442\u0430\u043D\u043E\u0432\u043B\u0435\u043D";
  existingPanel.hidden = false;
}
async function probeInstalledPackage() {
  const pathOut = await shell(`pm path ${PACKAGE_ID} 2>&1 || true`);
  const path = pathOut.split(/\r?\n/).map((s) => s.trim()).find((s) => /^package:/i.test(s))?.replace(/^package:/i, "")?.trim();
  if (!path) return null;
  const dump = await shell(`dumpsys package ${PACKAGE_ID} 2>&1 || true`);
  const versionName = dump.match(/versionName=(\S+)/)?.[1] || null;
  const versionCode = dump.match(/versionCode=(\d+)/)?.[1] || null;
  return { path, versionName, versionCode };
}
async function refreshInstalledPackageUi() {
  hideExistingPanel();
  if (!adb) return;
  log("\u041F\u0440\u043E\u0432\u0435\u0440\u044F\u044E, \u0443\u0441\u0442\u0430\u043D\u043E\u0432\u043B\u0435\u043D \u043B\u0438 JePlay\u2026", "dim");
  const info = await probeInstalledPackage();
  if (!info) {
    log("JePlay \u043D\u0430 \u0413\u0423 \u043D\u0435 \u043D\u0430\u0439\u0434\u0435\u043D \u2014 \u043C\u043E\u0436\u043D\u043E \u0441\u0442\u0430\u0432\u0438\u0442\u044C \u0441 \u043D\u0443\u043B\u044F.", "ok");
    setConnectedUi(true);
    return;
  }
  const label = info.versionName || info.versionCode || "?";
  log(`JePlay \u0443\u0436\u0435 \u0443\u0441\u0442\u0430\u043D\u043E\u0432\u043B\u0435\u043D: ${label}`, "ok");
  showExistingPanel(info);
  setConnectedUi(true);
}
async function uninstallInstalledPackage() {
  log(`\u0423\u0434\u0430\u043B\u044F\u044E ${PACKAGE_ID}\u2026`, "dim");
  let out = await shell(`pm uninstall --user 0 ${PACKAGE_ID} 2>&1 || true`);
  log(out.trim() || "(\u043F\u0443\u0441\u0442\u043E\u0439 \u043E\u0442\u0432\u0435\u0442 pm uninstall --user 0)", "dim");
  if (!/Success/i.test(out)) {
    out = await shell(`pm uninstall ${PACKAGE_ID} 2>&1 || true`);
    log(out.trim() || "(\u043F\u0443\u0441\u0442\u043E\u0439 \u043E\u0442\u0432\u0435\u0442 pm uninstall)", "dim");
  }
  if (!/Success/i.test(out)) {
    throw new Error(
      "\u041D\u0435 \u0443\u0434\u0430\u043B\u043E\u0441\u044C \u0443\u0434\u0430\u043B\u0438\u0442\u044C JePlay \u043F\u043E ADB. \u0423\u0434\u0430\u043B\u0438\u0442\u0435 \u0432\u0440\u0443\u0447\u043D\u0443\u044E: \u041D\u0430\u0441\u0442\u0440\u043E\u0439\u043A\u0438 \u2192 \u041F\u0440\u0438\u043B\u043E\u0436\u0435\u043D\u0438\u044F \u2192 JePlay \u2192 \u0423\u0434\u0430\u043B\u0438\u0442\u044C, \u0437\u0430\u0442\u0435\u043C \u043F\u043E\u0432\u0442\u043E\u0440\u0438\u0442\u0435 \u0443\u0441\u0442\u0430\u043D\u043E\u0432\u043A\u0443."
    );
  }
  const still = await shell(`pm path ${PACKAGE_ID} 2>&1 || true`);
  if (/package:/i.test(still)) {
    throw new Error("\u041F\u043E\u0441\u043B\u0435 uninstall \u043F\u0430\u043A\u0435\u0442 \u0432\u0441\u0451 \u0435\u0449\u0451 \u043D\u0430 \u043C\u0435\u0441\u0442\u0435");
  }
  log("JePlay \u0443\u0434\u0430\u043B\u0451\u043D", "ok");
  hideExistingPanel();
  setConnectedUi(true);
}
function normalizeVersion(text) {
  const line = text.split(/\r?\n/).map((s) => s.trim()).find((s) => s && !s.startsWith("#"));
  if (!line) return null;
  const match = line.match(/(?<![0-9])v?(\d+(?:\.\d+)+)/);
  return match ? match[1] : null;
}
function passwordV1(digits) {
  if (!/^\d{6,7}$/.test(digits)) return null;
  return String(BigInt(digits) * 802018n).slice(-6).padStart(6, "0");
}
function passwordV2(productId, rand) {
  if (!/^\d{6,20}$/.test(productId) || !/^\d{4}$/.test(rand)) return null;
  const value = V2_K * BigInt(productId) * BigInt(rand) % 1000000n;
  return value.toString().padStart(6, "0");
}
function refreshPassword() {
  const active = document.querySelector(".calc-tabs button.active")?.dataset.ver || "1";
  let password = null;
  if (active === "1") {
    password = passwordV1($("id-v1").value.trim());
  } else {
    password = passwordV2($("id-v2").value.trim(), $("rand-v2").value.trim());
  }
  if (!password) {
    passwordOut.className = "password-out empty";
    passwordOut.textContent = "\u0412\u0432\u0435\u0434\u0438\u0442\u0435 \u0434\u0430\u043D\u043D\u044B\u0435 \u0432\u044B\u0448\u0435";
    return;
  }
  passwordOut.className = "password-out";
  passwordOut.innerHTML = `<span>${password}</span><button type="button" class="btn btn-secondary" id="copy-pw" style="min-height:36px;padding:8px 12px;font-size:0.8rem">\u041A\u043E\u043F\u0438\u0440\u043E\u0432\u0430\u0442\u044C</button>`;
  $("copy-pw").onclick = async () => {
    try {
      await navigator.clipboard.writeText(password);
      $("copy-pw").textContent = "\u0421\u043A\u043E\u043F\u0438\u0440\u043E\u0432\u0430\u043D\u043E";
    } catch {
      $("copy-pw").textContent = "\u0412\u044B\u0434\u0435\u043B\u0438\u0442\u0435 \u0432\u0440\u0443\u0447\u043D\u0443\u044E";
    }
  };
}
document.querySelectorAll(".calc-tabs button").forEach((button) => {
  button.addEventListener("click", () => {
    document.querySelectorAll(".calc-tabs button").forEach((b) => {
      b.classList.toggle("active", b === button);
      b.setAttribute("aria-selected", b === button ? "true" : "false");
    });
    $("calc-v1").classList.toggle("hidden", button.dataset.ver !== "1");
    $("calc-v2").classList.toggle("hidden", button.dataset.ver !== "2");
    refreshPassword();
  });
});
["id-v1", "id-v2", "rand-v2"].forEach((id) => {
  $(id)?.addEventListener("input", refreshPassword);
});
async function shell(command) {
  if (adb.subprocess.spawnAndWaitLegacy) {
    return await adb.subprocess.spawnAndWaitLegacy(command);
  }
  if (adb.subprocess.spawnWaitText) {
    return await adb.subprocess.spawnWaitText(command);
  }
  const process = await adb.subprocess.spawn(command);
  const chunks = [];
  const reader = process.stdout.getReader();
  const decoder = new TextDecoder();
  while (true) {
    const { value, done } = await reader.read();
    if (done) break;
    chunks.push(decoder.decode(value, { stream: true }));
  }
  chunks.push(decoder.decode());
  try {
    await process.exit;
  } catch (_) {
  }
  return chunks.join("");
}
async function connectDevice() {
  trackGoal("connect_click");
  const Manager = AdbDaemonWebUsbDeviceManager.BROWSER;
  if (!Manager) {
    throw new Error("WebUSB \u043D\u0435\u0434\u043E\u0441\u0442\u0443\u043F\u0435\u043D. \u041E\u0442\u043A\u0440\u043E\u0439\u0442\u0435 \u044D\u0442\u0443 \u0441\u0442\u0440\u0430\u043D\u0438\u0446\u0443 \u0432 Chrome \u043F\u043E https:// \u0438\u043B\u0438 http://localhost");
  }
  if (!window.isSecureContext) {
    throw new Error("\u041D\u0443\u0436\u0435\u043D secure context: https:// \u0438\u043B\u0438 http://localhost (\u043D\u0435 file://)");
  }
  setStatus("\u0412\u044B\u0431\u0435\u0440\u0438\u0442\u0435 \u0443\u0441\u0442\u0440\u043E\u0439\u0441\u0442\u0432\u043E\u2026", "busy");
  log("\u041E\u0442\u043A\u0440\u044B\u0432\u0430\u044E \u043E\u043A\u043D\u043E \u0432\u044B\u0431\u043E\u0440\u0430 USB-\u0443\u0441\u0442\u0440\u043E\u0439\u0441\u0442\u0432\u0430 Chrome\u2026", "dim");
  const device = await Manager.requestDevice();
  if (!device) {
    throw new Error("\u0423\u0441\u0442\u0440\u043E\u0439\u0441\u0442\u0432\u043E \u043D\u0435 \u0432\u044B\u0431\u0440\u0430\u043D\u043E");
  }
  setStatus("\u041F\u043E\u0434\u043A\u043B\u044E\u0447\u0435\u043D\u0438\u0435\u2026", "busy");
  log(`\u0423\u0441\u0442\u0440\u043E\u0439\u0441\u0442\u0432\u043E: ${device.serial || "(\u0431\u0435\u0437 serial)"}`);
  log("\u0415\u0441\u043B\u0438 \u0413\u0423 \u0441\u043F\u0440\u043E\u0441\u0438\u0442 RSA \u2014 \u043F\u043E\u0434\u0442\u0432\u0435\u0440\u0434\u0438\u0442\u0435 \u0434\u043E\u0441\u0442\u0443\u043F", "dim");
  connection = await device.connect();
  const credentialStore = new AdbWebCredentialStore(CREDENTIAL_STORE_NAME);
  adb = new Adb(
    await AdbDaemonTransport.authenticate({
      serial: device.serial,
      connection,
      credentialStore
    })
  );
  setStatus(`\u041F\u043E\u0434\u043A\u043B\u044E\u0447\u0435\u043D\u043E: ${device.serial || "ADB"}`, "ok");
  log("ADB \u0433\u043E\u0442\u043E\u0432.", "ok");
  await refreshInstalledPackageUi();
  if (installedPackage) {
    log("\u0412\u044B\u0431\u0435\u0440\u0438\u0442\u0435: \u0443\u0434\u0430\u043B\u0438\u0442\u044C \u0438 \u043F\u043E\u0441\u0442\u0430\u0432\u0438\u0442\u044C \u0437\u0430\u043D\u043E\u0432\u043E, \u0438\u043B\u0438 \u043E\u0431\u043D\u043E\u0432\u0438\u0442\u044C \u043F\u043E\u0432\u0435\u0440\u0445.", "dim");
  } else {
    log("\u041D\u0430\u0436\u043C\u0438\u0442\u0435 \xAB\u0423\u0441\u0442\u0430\u043D\u043E\u0432\u0438\u0442\u044C\xBB.", "ok");
  }
}
async function disconnectDevice() {
  try {
    await adb?.close?.();
  } catch (_) {
  }
  try {
    await connection?.close?.();
  } catch (_) {
  }
  adb = null;
  connection = null;
  hideExistingPanel();
  hideSoftApPanel();
  setConnectedUi(false);
  setStatus("\u041E\u0442\u043A\u043B\u044E\u0447\u0435\u043D\u043E");
  log("\u041E\u0442\u043A\u043B\u044E\u0447\u0435\u043D\u043E", "dim");
}
async function fetchLatestRelease() {
  log(`\u0427\u0438\u0442\u0430\u044E ${VERSION_URL}\u2026`, "dim");
  const versionRes = await fetch(VERSION_URL, { cache: "no-store" });
  if (!versionRes.ok) throw new Error(`version.txt: HTTP ${versionRes.status}`);
  const version = normalizeVersion(await versionRes.text());
  if (!version) throw new Error("\u0412 version.txt \u043D\u0435\u0442 \u0440\u0430\u0441\u043F\u043E\u0437\u043D\u0430\u0432\u0430\u0435\u043C\u043E\u0439 \u0432\u0435\u0440\u0441\u0438\u0438");
  const apkName = `JePlay-${version}.apk`;
  const apkUrl = new URL(apkName, DOWNLOAD_BASE).href;
  log(`\u0412\u0435\u0440\u0441\u0438\u044F \u043D\u0430 \u0441\u0430\u0439\u0442\u0435: ${version}`);
  log(`\u0421\u043A\u0430\u0447\u0438\u0432\u0430\u044E ${apkUrl}\u2026`, "dim");
  const apkRes = await fetch(apkUrl, { cache: "no-store" });
  if (!apkRes.ok) throw new Error(`APK: HTTP ${apkRes.status} (${apkName})`);
  const buffer2 = new Uint8Array(await apkRes.arrayBuffer());
  if (buffer2.byteLength < 1024) throw new Error("\u0421\u043A\u0430\u0447\u0430\u043D\u043D\u044B\u0439 APK \u0441\u043B\u0438\u0448\u043A\u043E\u043C \u043C\u0430\u043B\u0435\u043D\u044C\u043A\u0438\u0439 \u2014 \u043F\u0440\u043E\u0432\u0435\u0440\u044C\u0442\u0435 \u0444\u0430\u0439\u043B \u043D\u0430 \u0441\u0435\u0440\u0432\u0435\u0440\u0435");
  log(`APK ${apkName}: ${(buffer2.byteLength / (1024 * 1024)).toFixed(2)} \u041C\u0411`);
  return { version, apkName, buffer: buffer2 };
}
function bufferToStream(buffer2) {
  return new ReadableStream({
    start(controller) {
      controller.enqueue(buffer2);
      controller.close();
    }
  });
}
async function pushBuffer(buffer2, remotePath) {
  log(`\u0417\u0430\u0433\u0440\u0443\u0437\u043A\u0430 \u2192 ${remotePath} (${(buffer2.byteLength / 1024).toFixed(0)} \u041A\u0411)`, "dim");
  const sync = await adb.sync();
  try {
    await sync.write({
      filename: remotePath,
      file: bufferToStream(buffer2)
    });
  } finally {
    try {
      if (typeof sync.dispose === "function") await sync.dispose();
      else if (typeof sync.close === "function") await sync.close();
    } catch (_) {
    }
  }
  await shell(`chmod 644 "${remotePath}"`);
}
async function pushAndInstall(buffer2) {
  const helperRes = await fetch(HELPER_URL, { cache: "no-store" });
  if (!helperRes.ok) {
    throw new Error(`\u041D\u0435 \u043D\u0430\u0439\u0434\u0435\u043D helper localinstall.apk \u043D\u0430 \u0441\u0430\u0439\u0442\u0435 (HTTP ${helperRes.status})`);
  }
  const helper = new Uint8Array(await helperRes.arrayBuffer());
  await pushBuffer(buffer2, REMOTE_TARGET);
  await pushBuffer(helper, REMOTE_HELPER);
  log("\u041F\u0440\u043E\u0431\u0443\u044E pm install\u2026", "dim");
  let output = await shell(`pm install -r -g "${REMOTE_TARGET}" 2>&1 || true`);
  log(output.trim() || "(\u043F\u0443\u0441\u0442\u043E\u0439 \u043E\u0442\u0432\u0435\u0442 pm install)", "dim");
  if (/Success/i.test(output)) {
    await shell(`rm -f "${REMOTE_TARGET}" "${REMOTE_HELPER}"`).catch(() => "");
    return;
  }
  if (/Restriction prevents installing/i.test(output)) {
    log("\u041F\u0440\u043E\u0448\u0438\u0432\u043A\u0430 \u0431\u043B\u043E\u043A\u0438\u0440\u0443\u0435\u0442 pm install \u2014 \u0441\u0442\u0430\u0432\u043B\u044E \u0447\u0435\u0440\u0435\u0437 PackageInstaller.Session\u2026", "dim");
  } else {
    log("pm install \u043D\u0435 \u0441\u0440\u0430\u0431\u043E\u0442\u0430\u043B \u2014 \u043F\u0440\u043E\u0431\u0443\u044E PackageInstaller.Session\u2026", "dim");
  }
  output = await shell(
    `CLASSPATH=${REMOTE_HELPER} /system/bin/app_process /system/bin LocalInstall ${REMOTE_TARGET} 120 2>&1 || true`
  );
  log(output.trim() || "(\u043F\u0443\u0441\u0442\u043E\u0439 \u043E\u0442\u0432\u0435\u0442 LocalInstall)");
  const statusMatch = output.match(/LOCALINSTALL_STATUS=(-?\d+)/);
  const status = statusMatch ? Number(statusMatch[1]) : null;
  const message = output.match(/LOCALINSTALL_MESSAGE=(.*)/)?.[1]?.trim() || "";
  const exception = output.match(/LOCALINSTALL_EXCEPTION=(.*)/)?.[1]?.trim() || "";
  await shell(`rm -f "${REMOTE_TARGET}" "${REMOTE_HELPER}"`).catch(() => "");
  if (status === 0) return;
  if (status === 1 && /PENDING_USER_ACTION/i.test(output + message)) {
    throw new Error(
      "\u041D\u0430 \u044D\u043A\u0440\u0430\u043D\u0435 \u0430\u0432\u0442\u043E\u043C\u043E\u0431\u0438\u043B\u044F \u043D\u0443\u0436\u043D\u043E \u043F\u043E\u0434\u0442\u0432\u0435\u0440\u0434\u0438\u0442\u044C \u0443\u0441\u0442\u0430\u043D\u043E\u0432\u043A\u0443 (\u0434\u0438\u0430\u043B\u043E\u0433 Install). \u041F\u043E\u0434\u0442\u0432\u0435\u0440\u0434\u0438\u0442\u0435 \u0438 \u043F\u043E\u0432\u0442\u043E\u0440\u0438\u0442\u0435."
    );
  }
  throw new Error(
    [
      "\u0423\u0441\u0442\u0430\u043D\u043E\u0432\u043A\u0430 \u043D\u0435 \u0443\u0434\u0430\u043B\u0430\u0441\u044C (Restriction / PackageInstaller).",
      status != null ? `status=${status}` : null,
      message || null,
      exception || null,
      !statusMatch ? output.trim().slice(0, 500) : null
    ].filter(Boolean).join("\n")
  );
}
async function grantInstallUnknownApps() {
  log("\u0420\u0430\u0437\u0440\u0435\u0448\u0430\u044E \u0443\u0441\u0442\u0430\u043D\u043E\u0432\u043A\u0443 \u043E\u0431\u043D\u043E\u0432\u043B\u0435\u043D\u0438\u0439 \u0438\u0437 JePlay (unknown apps)\u2026", "dim");
  await shell(`appops set ${PACKAGE_ID} REQUEST_INSTALL_PACKAGES allow 2>&1 || true`);
  await shell(`cmd appops set ${PACKAGE_ID} REQUEST_INSTALL_PACKAGES allow 2>&1 || true`);
  const check = await shell(`appops get ${PACKAGE_ID} REQUEST_INSTALL_PACKAGES 2>&1 || true`);
  const trimmed = check.trim();
  if (/allow/i.test(trimmed)) {
    log(`  REQUEST_INSTALL_PACKAGES: ${trimmed}`, "ok");
    return true;
  }
  log(`  REQUEST_INSTALL_PACKAGES: ${trimmed || "\u043D\u0435 \u043F\u043E\u0434\u0442\u0432\u0435\u0440\u0436\u0434\u0435\u043D\u043E"}`, "dim");
  log("  \u041E\u0431\u043D\u043E\u0432\u043B\u0435\u043D\u0438\u0435 \u043A\u043D\u043E\u043F\u043A\u043E\u0439 \u0432 JePlay \u043C\u043E\u0436\u0435\u0442 \u0441\u043D\u043E\u0432\u0430 \u0441\u043F\u0440\u043E\u0441\u0438\u0442\u044C Settings \u2014 \u043E\u0442\u043A\u0440\u043E\u0439\u0442\u0435 APK \u0438\u0437 Downloads.", "dim");
  return false;
}
async function grantPermissions() {
  await grantInstallUnknownApps();
  log("\u0412\u044B\u0434\u0430\u044E runtime-\u0440\u0430\u0437\u0440\u0435\u0448\u0435\u043D\u0438\u044F\u2026", "dim");
  for (const permission of RUNTIME_GRANTS) {
    const out = await shell(`pm grant ${PACKAGE_ID} ${permission} 2>&1 || true`);
    const trimmed = out.trim();
    if (trimmed && !/Success|already/i.test(trimmed)) {
      log(`  ${permission}: ${trimmed}`, "dim");
    } else {
      log(`  ${permission}: ok`, "dim");
    }
  }
  log("\u0412\u044B\u0434\u0430\u044E appops\u2026", "dim");
  for (const op of APPOPS) {
    if (op === "REQUEST_INSTALL_PACKAGES") continue;
    const out = await shell(`appops set ${PACKAGE_ID} ${op} allow 2>&1 || true`);
    const trimmed = out.trim();
    log(`  ${op}: ${trimmed || "ok"}`, "dim");
  }
}
function b64DecodeUtf8(b64) {
  if (!b64) return "";
  const binary = atob(b64);
  const bytes = Uint8Array.from(binary, (c) => c.charCodeAt(0));
  return new TextDecoder().decode(bytes);
}
function parseApConfigRead(output) {
  const line = String(output || "").split(/\r?\n/).map((s) => s.trim()).filter((s) => s.startsWith(APCONFIG_READ_HEADER)).pop();
  if (!line) return null;
  const body = line.slice(APCONFIG_READ_HEADER.length);
  if (!body.startsWith("ok|")) return { error: body || "failed" };
  const parts = body.split("|");
  const ssidB64 = parts[1] || "";
  const passB64 = parts[2] || "";
  let ssid = "";
  let passphrase = "";
  try {
    ssid = b64DecodeUtf8(ssidB64);
    passphrase = b64DecodeUtf8(passB64);
  } catch {
    return { error: "base64 decode failed" };
  }
  if (!ssid) return { error: "empty ssid" };
  return {
    ssid,
    passphrase,
    security: Number(parts[3] ?? "1") || 1,
    ssidB64,
    passB64
  };
}
function hideSoftApPanel() {
  if (!softapPanel) return;
  softapPanel.hidden = true;
  if (softapSsidEl) softapSsidEl.textContent = "";
  if (softapPassEl) softapPassEl.textContent = "";
}
function showSoftApCredentials(creds) {
  if (!softapPanel || !softapSsidEl || !softapPassEl) return;
  softapSsidEl.textContent = creds.ssid;
  softapPassEl.textContent = creds.passphrase || "(\u043E\u0442\u043A\u0440\u044B\u0442\u0430\u044F \u0441\u0435\u0442\u044C)";
  softapPanel.hidden = false;
}
async function copyField(text, button) {
  try {
    await navigator.clipboard.writeText(text);
    const prev = button.textContent;
    button.textContent = "\u0421\u043A\u043E\u043F\u0438\u0440\u043E\u0432\u0430\u043D\u043E";
    setTimeout(() => {
      button.textContent = prev;
    }, 1500);
  } catch {
    button.textContent = "\u0412\u044B\u0434\u0435\u043B\u0438\u0442\u0435 \u0432\u0440\u0443\u0447\u043D\u0443\u044E";
  }
}
async function readAndSeedSoftAp(apkPath) {
  hideSoftApPanel();
  const quotedApk = `'${apkPath.replace(/'/g, `'"'"'`)}'`;
  const command = `CLASSPATH=${quotedApk} app_process /system/bin ${HOTSPOT_HELPER_CLASS} apconfig-read 2>&1 || true`;
  log("\u0427\u0438\u0442\u0430\u044E SoftAP (SSID/\u043F\u0430\u0440\u043E\u043B\u044C) \u0447\u0435\u0440\u0435\u0437 ADB\u2026", "dim");
  const output = await shell(command);
  const trimmed = output.trim();
  if (trimmed) log(trimmed.split(/\r?\n/).slice(-6).join("\n"), "dim");
  const parsed = parseApConfigRead(output);
  if (!parsed || parsed.error) {
    log(
      `SoftAP \u043D\u0435 \u043F\u0440\u043E\u0447\u0438\u0442\u0430\u043D (${parsed?.error || "\u043D\u0435\u0442 \u043E\u0442\u0432\u0435\u0442\u0430"}). \u041F\u0430\u0440\u043E\u043B\u044C \u043C\u043E\u0436\u043D\u043E \u0432\u0432\u0435\u0441\u0442\u0438 \u0432 JePlay \u0441 iPhone.`,
      "dim"
    );
    return null;
  }
  log(`SoftAP: ${parsed.ssid}`, "ok");
  showSoftApCredentials(parsed);
  const importBody = [
    "DIPLAY_SOFTAP_IMPORT_V1",
    `ssid_b64=${parsed.ssidB64}`,
    `pass_b64=${parsed.passB64}`,
    `security=${parsed.security}`,
    ""
  ].join("\n");
  await pushBuffer(new TextEncoder().encode(importBody), SOFTAP_IMPORT_PATH);
  log(`\u0417\u0430\u043F\u0438\u0441\u0430\u043B \u043D\u0430\u0441\u0442\u0440\u043E\u0439\u043A\u0438 SoftAP \u2192 ${SOFTAP_IMPORT_PATH} (JePlay \u043F\u043E\u0434\u0445\u0432\u0430\u0442\u0438\u0442 \u043F\u0440\u0438 \u0437\u0430\u043F\u0443\u0441\u043A\u0435)`, "ok");
  return parsed;
}
async function installLatest({ uninstallFirst = false } = {}) {
  if (!adb) throw new Error("\u0421\u043D\u0430\u0447\u0430\u043B\u0430 \u043F\u043E\u0434\u043A\u043B\u044E\u0447\u0438\u0442\u0435 \u0443\u0441\u0442\u0440\u043E\u0439\u0441\u0442\u0432\u043E");
  trackGoal("install_click", { uninstallFirst: Boolean(uninstallFirst) });
  finalBanner.className = "final-banner";
  finalBanner.textContent = "";
  hideSoftApPanel();
  setStatus(uninstallFirst ? "\u041F\u0435\u0440\u0435\u0443\u0441\u0442\u0430\u043D\u043E\u0432\u043A\u0430\u2026" : "\u0423\u0441\u0442\u0430\u043D\u043E\u0432\u043A\u0430\u2026", "busy");
  actionBtn.disabled = true;
  if (reinstallBtn) reinstallBtn.disabled = true;
  if (uninstallFirst) {
    const current = installedPackage || await probeInstalledPackage();
    if (current) await uninstallInstalledPackage();
    else log("\u0423\u0434\u0430\u043B\u044F\u0442\u044C \u043D\u0435\u0447\u0435\u0433\u043E \u2014 JePlay \u0443\u0436\u0435 \u043E\u0442\u0441\u0443\u0442\u0441\u0442\u0432\u0443\u0435\u0442.", "dim");
  }
  const { version, buffer: buffer2 } = await fetchLatestRelease();
  await pushAndInstall(buffer2);
  await grantPermissions();
  const check = await shell(`pm path ${PACKAGE_ID} 2>&1 || true`);
  log(check.trim() || "(\u043F\u0430\u043A\u0435\u0442 \u043D\u0435 \u043D\u0430\u0439\u0434\u0435\u043D \u043F\u043E\u0441\u043B\u0435 \u0443\u0441\u0442\u0430\u043D\u043E\u0432\u043A\u0438)");
  if (!/package:/i.test(check)) {
    throw new Error("\u041F\u0430\u043A\u0435\u0442 ru.jeplay.app \u043D\u0435 \u043D\u0430\u0439\u0434\u0435\u043D \u043F\u043E\u0441\u043B\u0435 \u0443\u0441\u0442\u0430\u043D\u043E\u0432\u043A\u0438");
  }
  const apkPath = check.split(/\r?\n/).map((s) => s.trim()).find((s) => /^package:/i.test(s))?.replace(/^package:/i, "")?.trim();
  let softAp = null;
  if (apkPath) {
    softAp = await readAndSeedSoftAp(apkPath);
  }
  setStatus("\u0413\u043E\u0442\u043E\u0432\u043E", "ok");
  const softApHint = softAp ? ` SoftAP \xAB${softAp.ssid}\xBB \u0437\u0430\u043F\u0438\u0441\u0430\u043D \u0432 JePlay \u2014 \u043E\u0442\u043A\u0440\u043E\u0439\u0442\u0435 \u043F\u0440\u0438\u043B\u043E\u0436\u0435\u043D\u0438\u0435 \u043D\u0430 \u0413\u0423.` : " SoftAP \u0441 \u0413\u0423 \u043D\u0435 \u0443\u0434\u0430\u043B\u043E\u0441\u044C \u043F\u0440\u043E\u0447\u0438\u0442\u0430\u0442\u044C \u2014 \u043F\u0430\u0440\u043E\u043B\u044C \u0432\u0432\u0435\u0434\u0438\u0442\u0435 \u0432 JePlay \u0441 iPhone.";
  const modeHint = uninstallFirst ? "\u043F\u0435\u0440\u0435\u0443\u0441\u0442\u0430\u043D\u043E\u0432\u043B\u0435\u043D" : "\u0443\u0441\u0442\u0430\u043D\u043E\u0432\u043B\u0435\u043D";
  showFinal(true, `JePlay ${version} ${modeHint}.${softApHint}`);
  log(`\u0423\u0441\u043F\u0435\u0445: JePlay ${version}`, "ok");
  trackGoal("install_success", { version, uninstallFirst: Boolean(uninstallFirst) });
  await refreshInstalledPackageUi();
}
async function runInstallAction(options = {}) {
  if (busy) return;
  busy = true;
  actionBtn.disabled = true;
  if (reinstallBtn) reinstallBtn.disabled = true;
  try {
    if (!adb) await connectDevice();
    else await installLatest(options);
  } catch (error) {
    const message = String(error?.message || error);
    setStatus(message, "err");
    log(message, "err");
    if (adb) {
      showFinal(false, `\u041E\u0448\u0438\u0431\u043A\u0430 \u0443\u0441\u0442\u0430\u043D\u043E\u0432\u043A\u0438: ${message}`);
      await refreshInstalledPackageUi().catch(() => setConnectedUi(true));
    } else {
      hideExistingPanel();
      setConnectedUi(false);
    }
    console.error(error);
  } finally {
    busy = false;
    if (adb) {
      actionBtn.disabled = false;
      if (reinstallBtn) reinstallBtn.disabled = !installedPackage;
    } else {
      actionBtn.disabled = false;
      if (reinstallBtn) reinstallBtn.disabled = true;
    }
  }
}
$("copy-softap-ssid")?.addEventListener("click", () => {
  const text = softapSsidEl?.textContent?.trim();
  if (text) copyField(text, $("copy-softap-ssid"));
});
$("copy-softap-pass")?.addEventListener("click", () => {
  const text = softapPassEl?.textContent?.trim();
  if (text && text !== "(\u043E\u0442\u043A\u0440\u044B\u0442\u0430\u044F \u0441\u0435\u0442\u044C)") copyField(text, $("copy-softap-pass"));
});
actionBtn.addEventListener("click", () => runInstallAction({ uninstallFirst: false }));
reinstallBtn?.addEventListener("click", () => {
  if (!adb || !installedPackage) return;
  runInstallAction({ uninstallFirst: true });
});
disconnectBtn.addEventListener("click", async () => {
  if (busy) return;
  busy = true;
  try {
    await disconnectDevice();
  } finally {
    busy = false;
  }
});
if (!window.isSecureContext) {
  setStatus("\u041E\u0442\u043A\u0440\u043E\u0439\u0442\u0435 \u0447\u0435\u0440\u0435\u0437 http://localhost \u0438\u043B\u0438 https://", "err");
  log("WebUSB \u043D\u0435 \u0440\u0430\u0431\u043E\u0442\u0430\u0435\u0442 \u0441 file:// \u2014 \u043F\u043E\u0434\u043D\u0438\u043C\u0438\u0442\u0435 \u043B\u043E\u043A\u0430\u043B\u044C\u043D\u044B\u0439 \u0441\u0435\u0440\u0432\u0435\u0440 \u0438\u043B\u0438 \u043E\u0442\u043A\u0440\u043E\u0439\u0442\u0435 \u0441\u0430\u0439\u0442 \u043F\u043E HTTPS.", "err");
}
refreshPassword();
