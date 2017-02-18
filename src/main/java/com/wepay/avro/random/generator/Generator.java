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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;

public class Generator {

  private static final Schema.Parser schemaParser = new Schema.Parser();
  private static final Map<Schema, Generex> generexCache = new HashMap<>();
  private static final Map<Schema, List<Object>> optionsCache = new HashMap<>();

  public static final String ARG_PROPERTIES_PROP = "arg.properties";

  public static final String LENGTH_PROP = "length";
  public static final String LENGTH_PROP_MIN = "min";
  public static final String LENGTH_PROP_MAX = "max";

  public static final String REGEX_PROP = "regex";

  public static final String OPTIONS_PROP = "options";
  public static final String OPTIONS_PROP_FILE = "file";
  public static final String OPTIONS_PROP_ENCODING = "encoding";

  public static final String KEYS_PROP = "keys";

  public static final String RANGE_PROP = "range";
  public static final String RANGE_PROP_MIN = "min";
  public static final String RANGE_PROP_MAX = "max";

  public static final String ODDS_PROP = "odds";

  // TODO:    Enable the user to specify iteration for numeric types so that successive
  // TODO:  randomly-generated Avro objects have deterministically increasing/decreasing values.
  // TODO:  Also, possibly consider adding something like this for booleans (although it wouldn't
  // TODO:  be anything more than alternation between true and false).
  public static final String ITERATION_PROP = "iteration";
  public static final String ITERATION_PROP_MIN = "start";
  public static final String ITERATION_PROP_MAX = "restart";
  public static final String ITERATION_PROP_STEP = "step";

  // TODO:    Enable the user to specify that numeric/string types should be given the value of
  // TODO:  System.currentTimeMillis().
  public static final String TIME_PROP = "time";

  private final Schema topLevelSchema;
  private final Random random;

  private long iterations;

  public Generator(Schema topLevelSchema, Random random) {
    this.topLevelSchema = topLevelSchema;
    this.random = random;
    this.iterations = 0;
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
    return topLevelSchema;
  }

  public Object generate() {
    return generateObject(topLevelSchema);
  }

  public void resetIterations() {
    iterations = 0;
  }

  public long getIterations() {
    return iterations;
  }

  private Object generateObject(Schema schema) {
    Map propertiesProp = getProperties(schema).orElse(Collections.emptyMap());
    if (propertiesProp.containsKey(OPTIONS_PROP)) {
      return generateOption(schema, propertiesProp);
    }
//    if (propertiesProp.containsKey(ITERATION_PROP)) {
//      return generateIteration(schema, propertiesProp);
//    }
    switch (schema.getType()) {
      case ARRAY:
        return generateArray(schema, propertiesProp);
      case BOOLEAN:
        return generateBoolean(propertiesProp);
      case BYTES:
        return generateBytes(propertiesProp);
      case DOUBLE:
        return generateDouble(propertiesProp);
      case ENUM:
        return generateEnumSymbol(schema);
      case FIXED:
        return generateFixed(schema);
      case FLOAT:
        return generateFloat(propertiesProp);
      case INT:
        return generateInt(propertiesProp);
      case LONG:
        return generateLong(propertiesProp);
      case MAP:
        return generateMap(schema, propertiesProp);
      case NULL:
        return generateNull();
      case RECORD:
        return generateRecord(schema);
      case STRING:
        return generateString(schema, propertiesProp);
      case UNION:
        return generateUnion(schema);
      default:
        throw new RuntimeException("Unrecognized schema type: " + schema.getType());
    }
  }

  private Optional<Map> getProperties(Schema schema) {
    Object propertiesProp = schema.getObjectProp(ARG_PROPERTIES_PROP);
    if (propertiesProp == null) {
      return Optional.empty();
    } else if (propertiesProp instanceof Map) {
      return Optional.of((Map) propertiesProp);
    } else {
      throw new RuntimeException(String.format(
          "%s property must be given as object, was %s instead",
          ARG_PROPERTIES_PROP,
          propertiesProp.getClass().getName()
      ));
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
  private List<Object> parseOptions(Schema schema, Map propertiesProp) {
    if (propertiesProp.containsKey(LENGTH_PROP)) {
      throw new RuntimeException(String.format(
          "Cannot specify %s prop when %s prop is given",
          LENGTH_PROP,
          OPTIONS_PROP
      ));
    }
    if (propertiesProp.containsKey(REGEX_PROP)) {
      throw new RuntimeException(String.format(
          "Cannot specify %s prop when %s prop is given",
          REGEX_PROP,
          OPTIONS_PROP
      ));
    }
    Object optionsProp = propertiesProp.get(OPTIONS_PROP);
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
  private <T> T generateOption(Schema schema, Map propertiesProp) {
    if (!optionsCache.containsKey(schema)) {
      optionsCache.put(schema, parseOptions(schema, propertiesProp));
    }
    List<Object> options = optionsCache.get(schema);
    return (T) options.get(random.nextInt(options.size()));
  }

  private Collection<Object> generateArray(Schema schema, Map propertiesProp) {
    int length = getLengthBounds(propertiesProp).random();
    Collection<Object> result = new ArrayList<>(length);
    for (int i = 0; i < length; i++) {
      result.add(generateObject(schema.getElementType()));
    }
    return result;
  }

  private Boolean generateBoolean(Map propertiesProp) {
    Double odds = getDecimalNumberField(ARG_PROPERTIES_PROP, ODDS_PROP, "number", propertiesProp, 0.0, 1.0);
    if (odds == null) {
      return random.nextBoolean();
    } else {
      return random.nextDouble() < odds;
    }
  }

  private ByteBuffer generateBytes(Map propertiesProp) {
    byte[] bytes = new byte[getLengthBounds(propertiesProp.get(LENGTH_PROP)).random()];
    random.nextBytes(bytes);
    return ByteBuffer.wrap(bytes);
  }

  private Double generateDouble(Map propertiesProp) {
    Object rangeProp = propertiesProp.get(RANGE_PROP);
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

  private Float generateFloat(Map propertiesProp) {
    Object rangeProp = propertiesProp.get(RANGE_PROP);
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

  private Integer generateInt(Map propertiesProp) {
    Object rangeProp = propertiesProp.get(RANGE_PROP);
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

  private Long generateLong(Map propertiesProp) {
    Object rangeProp = propertiesProp.get(RANGE_PROP);
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

  private Map<String, Object> generateMap(Schema schema, Map propertiesProp) {
    Map<String, Object> result = new HashMap<>();
    int length = getLengthBounds(propertiesProp).random();
    Object keyProp = propertiesProp.get(KEYS_PROP);
    if (keyProp == null) {
      for (int i = 0; i < length; i++) {
        result.put(generateRandomString(1), generateObject(schema.getValueType()));
      }
    } else if (keyProp instanceof Map) {
      Map keyPropMap = (Map) keyProp;
      if (keyPropMap.containsKey(OPTIONS_PROP)) {
        if (!optionsCache.containsKey(schema)) {
          optionsCache.put(schema, parseOptions(Schema.create(Schema.Type.STRING), keyPropMap));
        }
        for (int i = 0; i < length; i++) {
          result.put(generateOption(schema, keyPropMap), generateObject(schema.getValueType()));
        }
      } else {
        int keyLength = getLengthBounds(keyPropMap.get(LENGTH_PROP)).random();
        for (int i = 0; i < length; i++) {
          result.put(
              generateRandomString(keyLength),
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

  private Object generateNull() {
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

  private String generateRandomString(int length) {
    byte[] bytes = new byte[length];
    for (int i = 0; i < length; i++) {
      bytes[i] = (byte) random.nextInt(128);
    }
    return new String(bytes, StandardCharsets.US_ASCII);
  }

  private String generateString(Schema schema, Map propertiesProp) {
    Object regexProp = propertiesProp.get(REGEX_PROP);
    if (regexProp != null) {
      return generateRegexString(schema, regexProp, getLengthBounds(propertiesProp));
    } else {
      return generateRandomString(getLengthBounds(propertiesProp).random());
    }
  }

  private Object generateUnion(Schema schema) {
    List<Schema> schemas = schema.getTypes();
    return generateObject(schemas.get(random.nextInt(schemas.size())));
  }

  private LengthBounds getLengthBounds(Map propertiesProp) {
    return getLengthBounds(propertiesProp.get(LENGTH_PROP));
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
