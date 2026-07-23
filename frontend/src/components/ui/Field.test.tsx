import { render, screen } from '@testing-library/react';
import { Field } from './Field';

test('에러가 있으면 alert 역할로 노출된다', () => {
  render(
    <Field id="email" label="이메일" value="" onChange={() => {}} error="이메일 형식이 아닙니다" />,
  );
  const alert = screen.getByRole('alert');
  expect(alert).toHaveTextContent('이메일 형식이 아닙니다');
});

test('에러 없으면 alert가 없다', () => {
  render(<Field id="email" label="이메일" value="" onChange={() => {}} />);
  expect(screen.queryByRole('alert')).toBeNull();
});
