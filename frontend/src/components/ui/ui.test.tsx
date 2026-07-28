import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { Button } from './Button';
import { Field } from './Field';
import { Price } from './Price';
import { Rating } from './Rating';

describe('Button', () => {
  it('loading=true면 클릭 핸들러가 호출되지 않고 aria-busy가 true다', () => {
    const onClick = vi.fn();
    render(
      <Button loading onClick={onClick}>
        제출
      </Button>,
    );

    const button = screen.getByRole('button', { name: '제출' });
    expect(button).toHaveAttribute('aria-busy', 'true');

    fireEvent.click(button);
    expect(onClick).not.toHaveBeenCalled();
  });
});

describe('Field', () => {
  it('label과 input을 htmlFor/id로 연결한다', () => {
    render(<Field label="이메일" id="email" value="" onChange={() => {}} />);

    const input = screen.getByLabelText('이메일');
    expect(input).toHaveAttribute('id', 'email');
  });

  it('에러가 있으면 aria-invalid와 aria-describedby를 설정한다', () => {
    render(
      <Field
        label="이메일"
        id="email"
        value=""
        onChange={() => {}}
        error="이메일 형식이 아닙니다"
      />,
    );

    const input = screen.getByLabelText('이메일');
    expect(input).toHaveAttribute('aria-invalid', 'true');
    const describedBy = input.getAttribute('aria-describedby');
    expect(describedBy).toBeTruthy();
    expect(document.getElementById(describedBy!)).toHaveTextContent('이메일 형식이 아닙니다');
  });
});

describe('Price', () => {
  it('할인율이 0이면 정가 취소선 노드를 렌더하지 않는다', () => {
    render(<Price listPrice={10000} salePrice={10000} discountRate={0} />);

    expect(screen.queryByText('10,000')).not.toBeInTheDocument();
    expect(screen.getByText('10,000원')).toBeInTheDocument();
  });

  it('할인율이 있으면 취소선 정가와 할인율을 표시한다', () => {
    render(<Price listPrice={20000} salePrice={15000} discountRate={25} />);

    expect(screen.getByText('25%')).toBeInTheDocument();
    expect(screen.getByText('15,000원')).toBeInTheDocument();
    expect(screen.getByText('20,000원')).toBeInTheDocument();
  });
});

describe('Rating', () => {
  it('reviewCount가 0이면 "첫 리뷰를 기다려요"를 보여준다', () => {
    render(<Rating rating={0} reviewCount={0} />);
    expect(screen.getByText('첫 리뷰를 기다려요')).toBeInTheDocument();
  });

  it('rating=4.3이면 접근성 텍스트에 4.3이 포함된다', () => {
    render(<Rating rating={4.3} reviewCount={12} />);
    expect(screen.getByText(/4\.3/)).toBeInTheDocument();
  });
});
