package org.jboss.resteasy.core;

import org.jboss.resteasy.spi.LoggableFailure;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.jboss.resteasy.spi.StringConverter;
import org.jboss.resteasy.util.HttpResponseCodes;
import org.jboss.resteasy.util.StringToPrimitive;

import javax.ws.rs.HeaderParam;
import javax.ws.rs.ext.RuntimeDelegate;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * @author <a href="mailto:bill@burkecentral.com">Bill Burke</a>
 * @version $Revision: 1 $
 */
public class StringParameterInjector
{
   protected Class type;
   protected Class baseType;
   protected Constructor constructor;
   protected Method valueOf;
   protected String defaultValue;
   protected String paramName;
   protected Class paramType;
   protected boolean isCollection;
   protected Class<? extends Collection> collectionType;
   protected AccessibleObject target;
   protected StringConverter converter;
   protected RuntimeDelegate.HeaderDelegate delegate;

   public StringParameterInjector()
   {

   }

   public StringParameterInjector(Class type, Type genericType, String paramName, Class paramType, String defaultValue, AccessibleObject target, ResteasyProviderFactory factory)
   {
      initialize(type, genericType, paramName, paramType, defaultValue, target, factory);
   }

   public boolean isCollectionOrArray()
   {
      return isCollection || type.isArray();
   }

   protected void initialize(Class type, Type genericType, String paramName, Class paramType, String defaultValue, AccessibleObject target, ResteasyProviderFactory factory)
   {
      this.type = type;
      this.paramName = paramName;
      this.paramType = paramType;
      this.defaultValue = defaultValue;
      this.target = target;

      baseType = type;
      if (type.isArray()) baseType = type.getComponentType();
      if (List.class.isAssignableFrom(type))
      {
         isCollection = true;
         collectionType = ArrayList.class;
      }
      else if (SortedSet.class.isAssignableFrom(type))
      {
         isCollection = true;
         collectionType = TreeSet.class;
      }
      else if (Set.class.isAssignableFrom(type))
      {
         isCollection = true;
         collectionType = HashSet.class;
      }
      if (isCollection)
      {
         if (genericType instanceof ParameterizedType)
         {
            ParameterizedType zType = (ParameterizedType) genericType;
            baseType = (Class) zType.getActualTypeArguments()[0];
         }
         else
         {
            baseType = String.class;
         }
      }
      if (!baseType.isPrimitive())
      {
         converter = factory.getStringConverter(baseType);
         if (converter != null) return;

         if (paramType.equals(HeaderParam.class))
         {
            delegate = factory.createHeaderDelegate(baseType);
            if (delegate != null) return;
         }


         try
         {
            constructor = baseType.getConstructor(String.class);
         }
         catch (NoSuchMethodException ignored)
         {

         }
         if (constructor == null)
         {
            try
            {
               valueOf = baseType.getDeclaredMethod("valueOf", String.class);
            }
            catch (NoSuchMethodException e)
            {
               throw new RuntimeException("Unable to find a constructor that takes a String param or a valueOf() method for " + getParamSignature() + " on " + target + " for basetype: " + baseType.getName());
            }

         }
      }
   }

   public String getParamSignature()
   {
      return paramType.getName() + "(\"" + paramName + "\")";
   }

   public Object extractValues(List<String> values)
   {
      if (values == null && (type.isArray() || isCollection) && defaultValue != null)
      {
         values = new ArrayList<String>(1);
         values.add(defaultValue);
      }
      if (type.isArray())
      {
         if (values == null) return null;
         Object vals = Array.newInstance(type.getComponentType(), values.size());
         for (int i = 0; i < values.size(); i++) Array.set(vals, i, extractValue(values.get(i)));
         return vals;
      }
      else if (isCollection)
      {
         if (values == null) return null;
         Collection collection = null;
         try
         {
            collection = collectionType.newInstance();
         }
         catch (Exception e)
         {
            throw new RuntimeException(e);
         }
         for (String str : values)
         {
            collection.add(extractValue(str));
         }
         return collection;
      }
      else
      {
         if (values == null) return extractValue(null);
         if (values.size() == 0) return extractValue(null);
         return extractValue(values.get(0));
      }

   }

   public Object extractValue(String strVal)
   {
      if (strVal == null)
      {
         if (defaultValue == null)
         {
            //System.out.println("NO DEFAULT VALUE");
            if (!baseType.isPrimitive()) return null;
         }
         else
         {
            strVal = defaultValue;
            //System.out.println("DEFAULT VAULUE: " + strVal);
         }
      }
      if (baseType.isPrimitive()) return StringToPrimitive.stringToPrimitiveBoxType(baseType, strVal);
      if (converter != null)
      {
         return converter.fromString(strVal);
      }
      else if (delegate != null)
      {
         return delegate.fromString(strVal);
      }
      else if (constructor != null)
      {
         try
         {
            return constructor.newInstance(strVal);
         }
         catch (InstantiationException e)
         {
            throw new RuntimeException("Unable to extract parameter from http request for " + getParamSignature() + " value is '" + strVal + "'" + " for " + target, e);
         }
         catch (IllegalAccessException e)
         {
            throw new RuntimeException("Unable to extract parameter from http request: " + getParamSignature() + " value is '" + strVal + "'" + " for " + target, e);
         }
         catch (InvocationTargetException e)
         {
            throw new RuntimeException("Unable to extract parameter from http request: " + getParamSignature() + " value is '" + strVal + "'" + " for " + target, e);
         }
      }
      else if (valueOf != null)
      {
         try
         {
            return valueOf.invoke(null, strVal);
         }
         catch (IllegalAccessException e)
         {
            throw new RuntimeException("Unable to extract parameter from http request: " + getParamSignature() + " value is '" + strVal + "'" + " for " + target, e);
         }
         catch (InvocationTargetException e)
         {
            throw new LoggableFailure("Unable to extract parameter from http request: " + getParamSignature() + " value is '" + strVal + "'" + " for " + target, e.getTargetException(), HttpResponseCodes.SC_NOT_FOUND);
         }
      }
      return null;
   }
}
