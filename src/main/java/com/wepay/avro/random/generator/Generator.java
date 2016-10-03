package com.wepay.avro.random.generator;

import com.mifmif.common.regex.Generex;

import org.apache.avro.Schema;

import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericEnumSymbol;
import org.apache.avro.generic.GenericFixed;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.generic.GenericRecordBuilder;

import org.apache.avro.io.DatumReader;
import org.apache.avro.io.Decoder;
import org.apache.avro.io.DecoderFactory;

import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

public class Generator {
  private static final String PROP_PREFIX = "arg.";
  private static final Schema.Parser schemaParser = new Schema.Parser();
  private static final Map<Schema, Generex> generexCache = new HashMap<>();
  private static final Map<Schema, List<Object>> optionsCache = new HashMap<>();

  public static final String LENGTH_PROP = PROP_PREFIX + "length";
  public static final String LENGTH_PROP_MIN = "min";
  public static final String LENGTH_PROP_MAX = "max";

  public static final String REGEX_PROP = PROP_PREFIX + "regex";

  public static final String OPTIONS_PROP = PROP_PREFIX + "options";
  public static final String OPTIONS_PROP_FILE = "file";
  public static final String OPTIONS_PROP_ENCODING = "encoding";

  public static final String KEYS_PROP = PROP_PREFIX + "keys";

  public static final String RANGE_PROP = PROP_PREFIX + "range";
  public static final String RANGE_PROP_MIN = "min";
  public static final String RANGE_PROP_MAX = "max";

  private final Schema schema;
  private final Random random;

  public Generator(Schema schema, Random random) {
    this.schema = schema;
    this.random = random;
  }

  public Generator(String schemaString, Random random) {
    this(schemaParser.parse(schemaString), random);
  }

  public Generator(InputStream schemaStream, Random random) throws IOException {
    this(schemaParser.parse(schemaStream), random);
  }

  public Generator(File schemaFile, Random random) throws IOException {
    this(schemaParser.parse(schemaFile), random);
  }

  public Schema schema() {
    return schema;
  }

  public Object generate() {
    return generateObject(schema);
  }

  private Object generateObject(Schema schema) {
    Object optionsProp = schema.getObjectProp(OPTIONS_PROP);
    if (optionsProp != null) {
      return generateOption(schema, optionsProp);
    }
    switch (schema.getType()) {
      case ARRAY:
        return generateArray(schema);
      case BOOLEAN:
        return generateBoolean(schema);
      case BYTES:
        return generateBytes(schema);
      case DOUBLE:
        return generateDouble(schema);
      case ENUM:
        return generateEnumSymbol(schema);
      case FIXED:
        return generateFixed(schema);
      case FLOAT:
        return generateFloat(schema);
      case INT:
        return generateInt(schema);
      case LONG:
        return generateLong(schema);
      case MAP:
        return generateMap(schema);
      case NULL:
        return generateNull(schema);
      case RECORD:
        return generateRecord(schema);
      case STRING:
        return generateString(schema);
      case UNION:
        return generateUnion(schema);
      default:
        throw new RuntimeException("Unrecognized schema type: " + schema.getType());
    }
  }

  @SuppressWarnings("unchecked")
  private Object wrapOption(Schema schema, Object option) {
    if (schema.getType() == Schema.Type.BYTES && option instanceof String) {
      option = ByteBuffer.wrap(((String) option).getBytes(Charset.defaultCharset()));
    } else if (schema.getType() == Schema.Type.FLOAT && option instanceof Double) {
      option = ((Double) option).floatValue();
    } else if (schema.getType() == Schema.Type.LONG && option instanceof Integer) {
      option = ((Integer) option).longValue();
    } else if (schema.getType() == Schema.Type.ARRAY && option instanceof Collection) {
      option = new GenericData.Array(schema, (Collection) option);
    } else if (schema.getType() == Schema.Type.ENUM && option instanceof String) {
      option = new GenericData.EnumSymbol(schema, (String) option);
    } else if (schema.getType() == Schema.Type.FIXED && option instanceof String) {
      option =
          new GenericData.Fixed(schema, ((String) option).getBytes(Charset.defaultCharset()));
    } else if (schema.getType() == Schema.Type.RECORD && option instanceof Map) {
      Map optionMap = (Map) option;
      GenericRecordBuilder optionBuilder = new GenericRecordBuilder(schema);
      for (Schema.Field field : schema.getFields()) {
        if (optionMap.containsKey(field.name())) {
          optionBuilder.set(field, optionMap.get(field.name()));
        }
      }
      option = optionBuilder.build();
    }
    return option;
  }

  @SuppressWarnings("unchecked")
  private List<Object> parseOptions(Schema schema, Object optionsProp) {
    if (schema.getObjectProp(LENGTH_PROP) != null) {
      throw new RuntimeException(String.format(
          "Cannot specify %s prop when %s prop is given",
          LENGTH_PROP,
          OPTIONS_PROP
      ));
    }
    if (schema.getObjectProp(REGEX_PROP) != null) {
      throw new RuntimeException(String.format(
          "Cannot specify %s prop when %s prop is given",
          REGEX_PROP,
          OPTIONS_PROP
      ));
    }
    if (optionsProp instanceof Collection) {
      Collection optionsList = (Collection) optionsProp;
      if (optionsList.isEmpty()) {
        throw new RuntimeException(String.format(
            "%s property cannot be empty",
            OPTIONS_PROP
        ));
      }
      List<Object> options = new ArrayList<>();
      for (Object option : optionsList) {
        option = wrapOption(schema, option);
        if (!GenericData.get().validate(schema, option)) {
          throw new RuntimeException(String.format(
              "Invalid option for %s schema: type %s, value '%s'",
              schema.getType().getName(),
              option.getClass().getName(),
              option
          ));
        }
        options.add(option);
      }
      return options;
    } else if (optionsProp instanceof Map) {
      Map optionsProps = (Map) optionsProp;
      Object optionsFile = optionsProps.get(OPTIONS_PROP_FILE);
      if (optionsFile == null) {
        throw new RuntimeException(String.format(
            "%s property must contain '%s' field when given as object",
            OPTIONS_PROP,
            OPTIONS_PROP_FILE
        ));
      }
      if (!(optionsFile instanceof String)) {
        throw new RuntimeException(String.format(
            "'%s' field of %s property must be given as string, was %s instead",
            OPTIONS_PROP_FILE,
            OPTIONS_PROP,
            optionsFile.getClass().getName()
        ));
      }
      Object optionsEncoding = optionsProps.get(OPTIONS_PROP_ENCODING);
      if (optionsEncoding == null) {
        throw new RuntimeException(String.format(
            "%s property must contain '%s' field when given as object",
            OPTIONS_PROP,
            OPTIONS_PROP_FILE
        ));
      }
      if (!(optionsEncoding instanceof String)) {
        throw new RuntimeException(String.format(
            "'%s' field of %s property must be given as string, was %s instead",
            OPTIONS_PROP_ENCODING,
            OPTIONS_PROP,
            optionsEncoding.getClass().getName()
        ));
      }
      try (InputStream optionsStream = new FileInputStream((String) optionsFile)) {
        DatumReader<Object> optionReader = new GenericDatumReader(schema);
        Decoder decoder;
        if ("binary".equals(optionsEncoding)) {
          decoder = DecoderFactory.get().binaryDecoder(optionsStream, null);
        } else if ("json".equals(optionsEncoding)) {
          decoder = DecoderFactory.get().jsonDecoder(schema, optionsStream);
        } else {
          throw new RuntimeException(String.format(
              "'%s' field of %s property only supports two formats: 'binary' and 'json'",
              OPTIONS_PROP_ENCODING,
              OPTIONS_PROP
          ));
        }
        List<Object> options = new ArrayList<>();
        Object option = optionReader.read(null, decoder);
        while (option != null) {
          option = wrapOption(schema, option);
          if (!GenericData.get().validate(schema, option)) {
            throw new RuntimeException(String.format(
                "Invalid option for %s schema: type %s, value '%s'",
                schema.getType().getName(),
                option.getClass().getName(),
                option
            ));
          }
          options.add(option);
          try {
            option = optionReader.read(null, decoder);
          } catch (EOFException eofe) {
            break;
          }
        }
        return options;
      } catch (FileNotFoundException fnfe) {
        throw new RuntimeException(
            String.format(
                "Unable to locate options file '%s'",
                optionsFile
            ),
            fnfe
        );
      } catch (IOException ioe) {
        throw new RuntimeException(
            String.format(
                "Unable to read options file '%s'",
                optionsFile
            ),
            ioe
        );
      }
    } else {
      throw new RuntimeException(String.format(
          "%s prop must be an array or an object, was %s instead",
          OPTIONS_PROP,
          optionsProp.getClass().getName()
      ));
    }
  }

  @SuppressWarnings("unchecked")
  private <T> T generateOption(Schema schema, Object optionsProp) {
    if (!optionsCache.containsKey(schema)) {
      optionsCache.put(schema, parseOptions(schema, optionsProp));
    }
    List<Object> options = optionsCache.get(schema);
    return (T) options.get(random.nextInt(options.size()));
  }

  private Collection<Object> generateArray(Schema schema) {
    int length = getLengthBounds(schema).random();
    Collection<Object> result = new ArrayList<>(length);
    for (int i = 0; i < length; i++) {
      result.add(generateObject(schema.getElementType()));
    }
    return result;
  }

  private Boolean generateBoolean(Schema schema) {
    return random.nextBoolean();
  }

  private ByteBuffer generateBytes(Schema schema) {
    byte[] bytes = new byte[getLengthBounds(schema.getObjectProp(LENGTH_PROP)).random()];
    random.nextBytes(bytes);
    return ByteBuffer.wrap(bytes);
  }

  private Double generateDouble(Schema schema) {
    Object rangeProp = schema.getObjectProp(RANGE_PROP);
    if (rangeProp != null) {
      if (rangeProp instanceof Map) {
        Map rangeProps = (Map) rangeProp;
        Double rangeMinField = getDecimalNumberField(RANGE_PROP, RANGE_PROP_MIN, rangeProps);
        Double rangeMaxField = getDecimalNumberField(RANGE_PROP, RANGE_PROP_MAX, rangeProps);
        double rangeMin = rangeMinField != null ? rangeMinField : -1 * Double.MAX_VALUE;
        double rangeMax = rangeMaxField != null ? rangeMaxField : Double.MAX_VALUE;
        if (rangeMin >= rangeMax) {
          throw new RuntimeException(String.format(
              "'%s' field must be strictly less than '%s' field in %s property",
              RANGE_PROP_MIN,
              RANGE_PROP_MAX,
              RANGE_PROP
          ));
        }
        return rangeMin + (random.nextDouble() * (rangeMax - rangeMin));
      } else {
        throw new RuntimeException(String.format(
            "%s property must be an object",
            RANGE_PROP
        ));
      }
    }
    return random.nextDouble();
  }

  private GenericEnumSymbol generateEnumSymbol(Schema schema) {
    List<String> enums = schema.getEnumSymbols();
    return new
        GenericData.EnumSymbol(schema, enums.get(random.nextInt(enums.size())));
  }

  private GenericFixed generateFixed(Schema schema) {
    byte[] bytes = new byte[schema.getFixedSize()];
    random.nextBytes(bytes);
    return new GenericData.Fixed(schema, bytes);
  }

  private Float generateFloat(Schema schema) {
    Object rangeProp = schema.getObjectProp(RANGE_PROP);
    if (rangeProp != null) {
      if (rangeProp instanceof Map) {
        Map rangeProps = (Map) rangeProp;
        Double rangeMinField = getDecimalNumberField(
            RANGE_PROP,
            RANGE_PROP_MIN,
            "float",
            rangeProps,
            (double) (-1 * Float.MAX_VALUE),
            (double) Float.MAX_VALUE
        );
        Double rangeMaxField = getDecimalNumberField(
            RANGE_PROP,
            RANGE_PROP_MAX,
            "float",
            rangeProps,
            (double) (-1 * Float.MAX_VALUE),
            (double) Float.MAX_VALUE
        );
        float rangeMin = rangeMinField != null ? rangeMinField.floatValue() : -1 * Float.MAX_VALUE;
        float rangeMax = rangeMaxField != null ? rangeMaxField.floatValue() : Float.MAX_VALUE;
        if (rangeMin >= rangeMax) {
          throw new RuntimeException(String.format(
              "'%s' field must be strictly less than '%s' field in %s property",
              RANGE_PROP_MIN,
              RANGE_PROP_MAX,
              RANGE_PROP
          ));
        }
        return rangeMin + (random.nextFloat() * (rangeMax - rangeMin));
      }
    }
    return random.nextFloat();
  }

  private Integer generateInt(Schema schema) {
    Object rangeProp = schema.getObjectProp(RANGE_PROP);
    if (rangeProp != null) {
      if (rangeProp instanceof Map) {
        Map rangeProps = (Map) rangeProp;
        Long rangeMinField = getIntegralNumberField(
            RANGE_PROP,
            RANGE_PROP_MIN,
            "int",
            rangeProps,
            (long) Integer.MIN_VALUE,
            (long) Integer.MAX_VALUE
        );
        Long rangeMaxField = getIntegralNumberField(
            RANGE_PROP,
            RANGE_PROP_MAX,
            "int",
            rangeProps,
            (long) Integer.MIN_VALUE,
            (long) Integer.MAX_VALUE
        );
        int rangeMin = rangeMinField != null ? rangeMinField.intValue() : Integer.MIN_VALUE;
        int rangeMax = rangeMaxField != null ? rangeMaxField.intValue() : Integer.MAX_VALUE;
        if (rangeMin >= rangeMax) {
          throw new RuntimeException(String.format(
              "'%s' field must be strictly less than '%s' field in %s property",
              RANGE_PROP_MIN,
              RANGE_PROP_MAX,
              RANGE_PROP
          ));
        }
        return rangeMin + ((int) (random.nextDouble() * (rangeMax - rangeMin)));
      }
    }
    return random.nextInt();
  }

  private Long generateLong(Schema schema) {
    Object rangeProp = schema.getObjectProp(RANGE_PROP);
    if (rangeProp != null) {
      if (rangeProp instanceof Map) {
        Map rangeProps = (Map) rangeProp;
        Long rangeMinField = getIntegralNumberField(RANGE_PROP, RANGE_PROP_MIN, rangeProps);
        Long rangeMaxField = getIntegralNumberField(RANGE_PROP, RANGE_PROP_MAX, rangeProps);
        long rangeMin = rangeMinField != null ? rangeMinField : Long.MIN_VALUE;
        long rangeMax = rangeMaxField != null ? rangeMaxField : Long.MAX_VALUE;
        if (rangeMin >= rangeMax) {
          throw new RuntimeException(String.format(
              "'%s' field must be strictly less than '%s' field in %s property",
              RANGE_PROP_MIN,
              RANGE_PROP_MAX,
              RANGE_PROP
          ));
        }
        return rangeMin + (((long) random.nextDouble() * (rangeMax - rangeMin)));
      }
    }
    return random.nextLong();
  }

  private Map<String, Object> generateMap(Schema schema) {
    Map<String, Object> result = new HashMap<>();
    int length = getLengthBounds(schema).random();
    Object keyProp = schema.getObjectProp(KEYS_PROP);
    if (keyProp == null) {
      for (int i = 0; i < length; i++) {
        result.put(generateRandomString(schema, 1), generateObject(schema.getValueType()));
      }
    } else if (keyProp instanceof Map) {
      Object optionsProp = ((Map) keyProp).get(OPTIONS_PROP);
      if (optionsProp != null) {
        if (!optionsCache.containsKey(schema)) {
          optionsCache.put(schema, parseOptions(Schema.create(Schema.Type.STRING), optionsProp));
        }
        for (Object option : optionsCache.get(schema)) {
          result.put((String) option, generateObject(schema.getValueType()));
        }
      } else {
        int keyLength = getLengthBounds(((Map) keyProp).get(LENGTH_PROP)).random();
        for (int i = 0; i < length; i++) {
          result.put(
              generateRandomString(schema, keyLength),
              generateObject(schema.getValueType())
          );
        }
      }
    } else {
      throw new RuntimeException(String.format(
          "%s prop must be an object",
          KEYS_PROP
      ));
    }
    return result;
  }

  private Object generateNull(Schema schema) {
    return null;
  }

  private GenericRecord generateRecord(Schema schema) {
    GenericRecordBuilder builder = new GenericRecordBuilder(schema);
    for (Schema.Field field : schema.getFields()) {
      builder.set(field, generateObject(field.schema()));
    }
    return builder.build();
  }

  @SuppressWarnings("unchecked")
  private String generateRegexString(Schema schema, Object regexProp, LengthBounds lengthBounds) {
    if (!generexCache.containsKey(schema)) {
      if (!(regexProp instanceof String)) {
        throw new RuntimeException(String.format("%s property must be a string", REGEX_PROP));
      }
      generexCache.put(schema, new Generex((String) regexProp));
    }
    // Generex.random(low, high) generates in range [low, high]; we want [low, high), so subtract
    // 1 from maxLength
    return generexCache.get(schema).random(lengthBounds.min(), lengthBounds.max() - 1);
  }

  private String generateRandomString(Schema schema, int length) {
    byte[] bytes = new byte[length];
    for (int i = 0; i < length; i++) {
      bytes[i] = (byte) random.nextInt(128);
    }
    return new String(bytes, StandardCharsets.US_ASCII);
  }

  private String generateString(Schema schema) {
    Object regexProp = schema.getObjectProp(REGEX_PROP);
    if (regexProp != null) {
      return generateRegexString(schema, regexProp, getLengthBounds(schema));
    } else {
      return generateRandomString(schema, getLengthBounds(schema).random());
    }
  }

  private Object generateUnion(Schema schema) {
    List<Schema> schemas = schema.getTypes();
    return generateObject(schemas.get(random.nextInt(schemas.size())));
  }

  private LengthBounds getLengthBounds(Schema schema) {
    return getLengthBounds(schema.getObjectProp(LENGTH_PROP));
  }

  private LengthBounds getLengthBounds(Object lengthProp) {
    if (lengthProp == null) {
      return new LengthBounds();
    } else if (lengthProp instanceof Integer) {
      return new LengthBounds((Integer) lengthProp);
    } else if (lengthProp instanceof Map) {
      Map lengthProps = (Map) lengthProp;
      Long minLength = getIntegralNumberField(
          LENGTH_PROP,
          LENGTH_PROP_MIN,
          lengthProps,
          0L,
          (long) (Integer.MAX_VALUE - 1)
      );
      Long maxLength = getIntegralNumberField(
          LENGTH_PROP,
          LENGTH_PROP_MAX,
          lengthProps,
          1L,
          (long) Integer.MAX_VALUE
      );
      if (minLength == null && maxLength == null) {
        throw new RuntimeException(String.format(
            "%s property must contain at least one of '%s' or '%s' fields when given as object",
            LENGTH_PROP,
            LENGTH_PROP_MIN,
            LENGTH_PROP_MAX
        ));
      }
      minLength = minLength != null ? minLength : 0;
      maxLength = maxLength != null ? maxLength : Integer.MAX_VALUE;
      return new LengthBounds(minLength.intValue(), maxLength.intValue());
    } else {
      throw new RuntimeException(String.format(
          "%s property must either be an integral number or an object, was %s instead",
          LENGTH_PROP,
          lengthProp.getClass().getName()
      ));
    }
  }

  private Long getIntegralNumberField(
      String property,
      String field,
      String type,
      Map propsMap,
      Long min,
      Long max) {
    Long result = getIntegralNumberField(property, field, propsMap);
    if (result != null && (result < min || result > max)) {
      throw new RuntimeException(String.format(
          "'%s' field of %s property must be in the range [%d, %d] for type %s",
          field,
          property,
          min,
          max,
          type
      ));
    }
    return result;
  }

  private Long getIntegralNumberField(
      String property,
      String field,
      Map propsMap,
      Long min,
      Long max) {
    Long result = getIntegralNumberField(property, field, propsMap);
    if (result != null && (result < min || result > max)) {
      throw new RuntimeException(String.format(
          "'%s' field of %s property must be in the range [%d, %d]",
          field,
          property,
          min,
          max
      ));
    }
    return result;
  }

  private Long getIntegralNumberField(String property, String field, Map propsMap) {
    Object result = propsMap.get(field);
    if (result == null || result instanceof Long) {
      return (Long) result;
    } else if (result instanceof Integer) {
      return ((Integer) result).longValue();
    } else {
      throw new RuntimeException(String.format(
          "'%s' field of %s property must be an integral number, was %s instead",
          field,
          property,
          result.getClass().getName()
      ));
    }
  }

  private Double getDecimalNumberField(
      String property,
      String field,
      String type,
      Map propsMap,
      Double min,
      Double max) {
    Double result = getDecimalNumberField(property, field, propsMap);
    if (result != null && (result < min || result > max)) {
      throw new RuntimeException(String.format(
          "'%s' field of %s property must be in the range [%e, %e] for type %s",
          field,
          property,
          min,
          max,
          type
      ));

    }
    return result;
  }

  private Double getDecimalNumberField(String property, String field, Map propsMap) {
    Object result = propsMap.get(field);
    if (result == null || result instanceof Double) {
      return (Double) result;
    } else if (result instanceof Float) {
      return ((Float) result).doubleValue();
    } else if (result instanceof Integer) {
      return ((Integer) result).doubleValue();
    } else if (result instanceof Long) {
      return ((Long) result).doubleValue();
    } else {
      throw new RuntimeException(String.format(
          "'%s' field of %s property must be a number, was %s instead",
          field,
          property,
          result.getClass().getName()
      ));
    }
  }

  private static class Bounds {
    protected final int min;
    protected final int max;

    public Bounds(int min, int max) {
      if (min >= max) {
        throw new IllegalArgumentException("max must be strictly greater than min");
      }
      this.min = min;
      this.max = max;
    }

    public int min() {
      return min;
    }

    public int max() {
      return max;
    }

    @Override
    public boolean equals(Object that) {
      if (!(that instanceof Bounds)) {
        return false;
      }
      Bounds thatBounds = (Bounds) that;
      return min == thatBounds.min() && max == thatBounds.max();
    }

    @Override
    public int hashCode() {
      return Objects.hash(min, max);
    }

    @Override
    public String toString() {
      return String.format("%s(%d, %d)", getClass().getName(), min, max);
    }
  }

  private class LengthBounds extends Bounds {
    public static final int DEFAULT_MIN = 8;
    public static final int DEFAULT_MAX = 16;

    public LengthBounds(int min, int max) {
      super(min, max);
      if (min < 0) {
        throw new IllegalArgumentException("min must be at least zero");
      }
    }

    public LengthBounds(int exact) {
      this(exact, exact + 1);
    }

    public LengthBounds() {
      this(DEFAULT_MIN, DEFAULT_MAX);
    }

    public int random() {
      return min + random.nextInt(max - min);
    }
  }
}
