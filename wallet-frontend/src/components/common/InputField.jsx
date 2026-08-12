function InputField({
  type,
  placeholder,
  value,
  onChange,
  name
}) {
  return (
    <input
      type={type}
      name={name}
      placeholder={placeholder}
      value={value}
      onChange={onChange}
    />
  );
}

export default InputField;