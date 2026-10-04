export const definitions={
 SETTINGS:{label:'Business settings',fields:{orderingEnabled:'Accept online orders',reservationsEnabled:'Accept reservation requests',orderPhoneRequired:'Require verified mobile for orders',reservationPhoneRequired:'Require verified mobile for reservations',orderSms:'Send SMS order updates',orderEmail:'Send email order updates when email exists',reservationSms:'Send SMS reservation updates',reservationEmail:'Send email reservation updates when email exists',paypalEnabled:'Enable PayPal checkout',payidEnabled:'Enable PayID checkout',payAtRestaurantEnabled:'Offer pay at restaurant'}},
 PAYPAL:{label:'PayPal',fields:{environment:'Environment',clientId:'Client ID'},secrets:{clientSecret:'Client secret'}},
 PAYID:{label:'PayID',fields:{identifier:'PayID identifier',accountName:'Recipient/account name'}},
 TWILIO:{label:'Twilio',fields:{accountSid:'Account SID',verifyServiceSid:'Verify Service SID',smsFrom:'SMS sender',verifySmsEnabled:'SMS verification capability',verifyEmailEnabled:'Generic email verification capability'},secrets:{authToken:'Auth token'}},
 SMTP:{label:'Email / SMTP',fields:{host:'SMTP host',port:'SMTP port',username:'SMTP username',from:'Sender email',starttls:'Require STARTTLS'},secrets:{password:'SMTP password'}}
};
export const booleanFields=new Set([...Object.keys(definitions.SETTINGS.fields),'verifySmsEnabled','verifyEmailEnabled','starttls']);
export function dependencyWarnings(fields,configuration){const warnings=[];
 const required=(keys,ready,message)=>{if(keys.some(k=>fields[k]==='true')&&!ready)warnings.push(message);};
 required(['paypalEnabled'],configuration.PAYPAL.configured,'Configure PayPal before enabling checkout.');
 if(fields.paypalEnabled==='true'&&configuration.PAYPAL.fields?.environment==='live'&&configuration.PAYPAL.validationStatus!=='VALID')warnings.push('Test live PayPal credentials successfully before enabling checkout.');
 if((fields.orderSms==='true'&&fields.orderPhoneRequired==='false')||(fields.reservationSms==='true'&&fields.reservationPhoneRequired==='false'))warnings.push('SMS updates require verified mobile contacts.');
 required(['payidEnabled'],configuration.PAYID.configured,'Configure PayID identifier and recipient name first.');
 required(['orderPhoneRequired','reservationPhoneRequired'],configuration.TWILIO.smsVerificationConfigured,'Configure Twilio SMS Verify before requiring verified mobiles.');
 required(['orderSms','reservationSms'],configuration.TWILIO.smsSendingConfigured,'Configure an SMS sender before enabling SMS updates.');
 required(['orderEmail','reservationEmail'],configuration.SMTP.configured,'Configure SMTP before enabling email updates.');
 if(fields.orderingEnabled==='true'&&['paypalEnabled','payidEnabled','payAtRestaurantEnabled'].every(k=>fields[k]!=='true'))warnings.push('Online ordering requires an available payment method.');
 return warnings;
}
export function replacementBody(version,fields,secrets={},clearSecrets=[],acknowledgePendingPayments=false){return{version,fields,secrets:Object.fromEntries(Object.entries(secrets).filter(([,v])=>v.trim()!=='')),clearSecrets,acknowledgePendingPayments};}
