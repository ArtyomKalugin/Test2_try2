package itis.parsing2;

import itis.parsing2.annotations.Concatenate;
import itis.parsing2.annotations.NotBlank;

import java.io.*;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

public class FactoryParsingServiceImpl implements FactoryParsingService {
    HashMap<String, String> parsedData = new HashMap<>();
    ArrayList<String> departments = new ArrayList<>();

    @Override
    public Factory parseFactoryData(String factoryDataDirectoryPath) throws FactoryParsingException {

        try{
            File dir = new File(factoryDataDirectoryPath);

            for(File file : dir.listFiles()){
                parseFile(file.getAbsolutePath());
            }

            Class<Factory> factoryClass = Factory.class;
            Constructor<? extends Factory> constructor = factoryClass.getDeclaredConstructor();
            constructor.setAccessible(true);
            Factory factory = constructor.newInstance();

            Field[] fields = factoryClass.getDeclaredFields();

            try{
                return processAnnotations(fields, factory);

            } catch (FactoryParsingException q){
                q.printStackTrace();
                q.getValidationErrors().stream().
                        map(s -> "field: " + s.getFieldName() + "\t" + "error: " + s.getValidationError()).
                        forEach(System.out::println);
            }

        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }


        return null;
    }

    private void parseFile(String path){
        try{
            File file = new File(path);
            FileReader reader = new FileReader(file);
            BufferedReader bf = new BufferedReader(reader);
            String result;

            while((result = bf.readLine()) != null){
                if(!result.equals("---")){
                    String[] line = result.split(":");
                    String key = format_string(line[0]);
                    String value;

                    if(line.length < 2){
                        value = null;
                    } else{
                        value = format_string(line[1]);
                    }

                    if(key.equals("departments")){
                        String[] values = value.split(",");

                        departments.addAll(Arrays.asList(values));
                    } else if(key.equals("description") && line.length >= 2){
                        parsedData.put(key, line[1].replaceAll("\"", ""));
                    } else{
                        parsedData.put(key, value);
                    }
                }
            }

        } catch (FileNotFoundException e){
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private Factory processAnnotations(Field[] fields, Factory factory) throws IllegalAccessException, FactoryParsingException {
        ArrayList<FactoryParsingException.FactoryValidationError> errors = new ArrayList<>();

        for(Field field : fields){
            if(field.getName().equals("departments")){
                field.setAccessible(true);

                field.set(factory, departments);

            } else if(field.getAnnotations().length == 0){
                field.setAccessible(true);

                field.set(factory, parsedData.get(field.getName()));
            }


            for(Annotation annotation : field.getAnnotations()){
                if(annotation instanceof NotBlank){
                    if(parsedData.get(field.getName()) == null || parsedData.get(field.getName()).equals("")){
                        errors.add(new FactoryParsingException.FactoryValidationError(field.getName(),
                                "The value is null or empty string"));

                        break;

                    } else{
                        field.setAccessible(true);

                        field.set(factory, parsedData.get(field.getName()));
                    }
                }

                if(annotation instanceof Concatenate){
                    String result = "";
                    ArrayList<String> toJoin = new ArrayList<>();

                    for(String fieldName : ((Concatenate) annotation).fieldNames()){
                        if(parsedData.containsKey(fieldName)){
                            toJoin.add(parsedData.get(fieldName));
                        } else {
                            errors.add(new FactoryParsingException.FactoryValidationError(field.getName(),
                                    "Filed named " + fieldName + " is not exist"));

                            break;
                        }

                        result = String.join(((Concatenate) annotation).delimiter(), toJoin);
                        field.setAccessible(true);

                        field.set(factory, result);
                    }
                }
            }

        }

        if(errors.size() > 0){
            throw new FactoryParsingException("Can't parse this file", errors);
        }

        return factory;
    }

    private String format_string(String str){
        String newStr = "";

        for(char elem : str.toCharArray()){

            if(elem != '\"' && elem != ' ' && elem != '[' && elem != ']'){
                newStr += elem;
            }
        }

        return newStr;
    }
}
