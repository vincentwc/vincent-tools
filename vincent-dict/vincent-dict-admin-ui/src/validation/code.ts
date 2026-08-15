export const CODE_PATTERN = /^[A-Z][A-Z0-9_]{0,63}$/;
export const CODE_PATTERN_MESSAGE = '编码须为大写英文、数字或下划线，且以字母开头';

export function isValidCode(value: string): boolean {
  return CODE_PATTERN.test(value);
}
