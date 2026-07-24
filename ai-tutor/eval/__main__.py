"""允许通过 python -m eval 运行 CLI"""
from eval.run_eval import main
import asyncio

if __name__ == "__main__":
    asyncio.run(main())
